# Lima VM `igbuild`: parallel IG Publisher builds

Docker Desktop's VM is capped at ~7.7 GiB, so builds there run one at a time. `igbuild` is a separate
Lima VM (Ubuntu 24.04, rootful Docker Engine) sized for many builds at once. Docker Desktop is
untouched; both daemons can run side by side.

| | |
|---|---|
| Config | `lima/igbuild.yaml` (self-contained; derived from Lima's `docker-rootful` template) |
| VM | vmType `vz`, aarch64, 16 CPUs, 96 GiB RAM, 300 GiB disk (sparse), Ubuntu 24.04 |
| Mount | `~/fhir-ig-tools` → same path in the VM, **writable**, virtiofs. Nothing else from `$HOME` is mounted. |
| Docker socket on host | `~/.lima/igbuild/sock/docker.sock` (Lima `portForwards` of `/var/run/docker.sock`) |
| Docker context | `igbuild` (not made current; Docker Desktop's `desktop-linux` stays the default) |

## Commands

```bash
# create + start (first time; ~1 min, installs Docker via get.docker.com)
limactl start --tty=false --name=igbuild ~/fhir-ig-tools/lima/igbuild.yaml
# host-side docker context (once)
docker context create igbuild --docker "host=unix://$HOME/.lima/igbuild/sock/docker.sock"

limactl stop igbuild          # stop (frees the 96 GiB)
limactl start igbuild         # start again (config is kept in ~/.lima/igbuild)
limactl shell igbuild         # shell in the VM
limactl delete igbuild        # remove it entirely (then re-create with the first command)

docker --context igbuild info   # expect CPUs: 16, Total Memory: ~94 GiB
```

Changing CPU/memory/disk later: `limactl edit igbuild --cpus N --memory M` (VM must be stopped), or
edit `igbuild.yaml`, `limactl delete igbuild` and re-create.

## Image

The image must exist on this daemon too. **Build it on the same base digest as Docker Desktop**:
`ghcr.io/fhir/ig-publisher-localdev:latest` moved on 2026-11-19 (new `:latest` = Debian trixie,
Ruby 3.4.13, kramdown 2.6.1), while our reference (D6) is Ruby 3.2.3 / kramdown 2.5.2 / Jekyll 4.4.1.
So pin the base by digest and tag it `:latest` locally before building:

```bash
D=sha256:39509f4138197f0b93a99ed3ae00e6dce2bbd76df1a2e4e2bd6dec4180dc34b5   # = Docker Desktop's copy
docker --context igbuild pull ghcr.io/fhir/ig-publisher-localdev@$D
docker --context igbuild tag  ghcr.io/fhir/ig-publisher-localdev@$D ghcr.io/fhir/ig-publisher-localdev:latest
docker --context igbuild build --pull=false -t ig-publisher-localdev-ca ~/fhir-ig-tools/docker
```

Verified identical to Docker Desktop's image (ruby -v, gem versions, OS). Check with
`docker --context X run --rm --entrypoint bash ig-publisher-localdev-ca -c 'ruby -v; gem list | grep -E "^(jekyll|liquid|kramdown|rouge)"'`.

## Using it from the scripts

All docker-using scripts honor the docker CLI's own `DOCKER_CONTEXT` / `DOCKER_HOST` env vars; unset =
current context (Docker Desktop), exactly as before.

```bash
DOCKER_CONTEXT=igbuild scripts/golden-build.sh <repo-name> [6g]
DOCKER_CONTEXT=igbuild scripts/golden-batch.sh -j 8 -m -Xmx8g -f names.txt
DOCKER_CONTEXT=igbuild ./build.sh --fast liquid        # jekyll/run.sh, jekyll/oracle/oracle.sh likewise
# or: DOCKER_HOST=unix://$HOME/.lima/igbuild/sock/docker.sock ...
```

`golden-batch.sh`: `-j N` parallel jobs (default 4), `-m HEAP` per-build heap (default `-Xmx6g`),
`-f FILE` names one per line, `-s` one shared writable package cache instead of per-job overlays (below). Per-build console in
`work/logs/<name>.log`, summary in `work/batch-summary.tsv` (name, valid, full_build_seconds,
jekyll_seconds, exit). Sizing: a build uses roughly its heap + ~1 GiB, so `-j 10 -m 7g` fits in 96 GiB;
US Core-sized IGs want `-m 10g` or more.

## TLS-inspecting proxy

The VM trusts `docker/extra-ca/proxy-root.pem` in the OS store via Lima's `caCerts.files` (cloud-init
`ca_certs`, applied before provisioning), with a provision-time `update-ca-certificates` fallback. That
covers apt, curl/get.docker.com, and dockerd's registry pulls (ghcr.io). The image adds the same CA
for Java/Node (docker/Dockerfile). Host-side `limactl` downloads use the macOS keychain, which already
trusts it.

## Gotchas

1. **Keep bulk file I/O on the VM's own disk, not the shared folder.** virtiofs (Apple
   Virtualization.framework) keeps one macOS file handle open for every file the guest has cached,
   and macOS caps open files system-wide (`kern.maxfiles`, 491,520). IG builds, goldens and the
   package cache are millions of files, so doing that I/O through the shared folder exhausts the
   Mac's file table (ENFILE, `Too many open files in system`) for every process on it, not just the
   builds. The scripts therefore keep all heavy data on the VM disk under `/srv/ig`
   (`scripts/lib/vmdata.sh`): build copies, golden `jekyll-in`/`jekyll-out`/`shim-out` and the
   package cache. They stream IG sources in with tar over the Docker socket (the Mac reads them
   natively), mount the VM dirs over the usual paths inside containers, and keep only small results
   (logs, `VALID`, timings, diff summaries) under `work/` on the Mac. To look at a tree from the Mac,
   copy it out with `from_vm` (same file). An earlier workaround, a guest timer that dropped the inode cache every
   10 s, only delayed the problem under parallel load and has been removed. Verified: the full
   exact comparison (234 goldens, 6 parallel) and corpus builds (8 parallel) left
   `sysctl kern.num_files` flat at its idle level (~7k). On a VM created before the change, run
   `limactl shell igbuild sudo systemctl disable --now drop-inode-cache.timer` once.
2. **Package cache.** `$VMDATA/fhir-cache` is shared, but `golden-batch.sh` gives each job its own
   overlay volume over it (the shared cache is the read-only lower layer; the job writes to its own
   upper layer). Concurrent builds therefore can't collide on package locks or delete packages
   under each other (both happened in the first corpus run). Complete packages a job downloaded are
   merged back afterwards, never overwriting. `-s` opts out (one shared, writable cache).
3. `ghcr.io/fhir/ig-publisher-localdev:latest` drifts (see Image). Don't `docker build --pull` blindly.
4. Files written by containers (which run as root) show up on the host owned by your user (virtiofs
   maps them), same as with Docker Desktop.
5. Lima re-runs `provision` scripts at every boot. They're idempotent.
