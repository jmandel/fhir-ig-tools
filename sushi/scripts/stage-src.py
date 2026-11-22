#!/usr/bin/env python3
"""Stream a SUSHI-relevant copy of an IG repo as a tar to stdout (for scripts/corpus-diff.sh -> the VM).

usage: stage-src.py <repo-dir> | docker run ... tar -xf - -C <dest>
Skips .git, output, temp, template, input-cache, fsh-generated, node_modules and _preprocessed. Files over 256 KB that
SUSHI never reads (anything but .json/.xml/.fsh/.yaml/.yml/.ini/.md/.txt/.html) are staged EMPTY under the same name,
so directory listings (page folders, predefined-resource folders) stay as they are while the copy stays small. Both
implementations then run on the same staged copy.
"""
import os, sys, tarfile

SKIP_DIRS = {'.git', 'output', 'temp', 'template', 'input-cache', 'fsh-generated', 'node_modules', '_preprocessed'}
READ_EXT = ('.json', '.xml', '.fsh', '.yaml', '.yml', '.ini', '.md', '.txt', '.html')
LIMIT = 256 * 1024

def main():
    root = sys.argv[1]
    with tarfile.open(fileobj=sys.stdout.buffer, mode='w|') as tar:
        for d, dirs, files in os.walk(root, followlinks=False):
            rel = os.path.relpath(d, root)
            if rel == '.':
                dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
            else:
                dirs[:] = [x for x in dirs if x not in ('.git', 'node_modules')]
            for name in dirs + files:
                p = os.path.join(d, name)
                arc = os.path.normpath(os.path.join(rel, name))
                if os.path.islink(p):
                    tar.add(p, arcname=arc, recursive=False)
                    continue
                if name in dirs:
                    tar.add(p, arcname=arc, recursive=False)
                    continue
                st = os.stat(p)
                if st.st_size > LIMIT and not name.lower().endswith(READ_EXT):
                    ti = tar.gettarinfo(p, arcname=arc)
                    ti.size = 0
                    tar.addfile(ti)
                else:
                    tar.add(p, arcname=arc, recursive=False)

main()
