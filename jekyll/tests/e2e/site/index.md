---
title: Home
---
# {{ site.title }}

{% include badge.html label="draft" %}

| Resource | FMM |
|---|---|
{% for i in site.data.items.items %}| {{ i.name }} | {{ i.fmm }} |
{% endfor %}

Mature: {{ site.data.items.items | where: "fmm", 5 | map: "name" | join: ", " }}

```json
{"resourceType": "Patient", "id": "example", "_birthDate": {"extension": []}}
```

Some _emphasis_ and **strong** and a "quote".
