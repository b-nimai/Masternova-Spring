"""Turns every PromQL query in the Grafana dashboards into a recording rule, so that
`promtool check rules` parses them: a typo in a panel fails CI instead of showing "No data".

Usage: python3 dashboards_as_rules.py <dashboards dir> <output rules file>
Grafana's own variables are replaced by values PromQL accepts. Also checks that every panel uses
the provisioned Prometheus datasource uid, and that dashboard uids are unique.
"""

import json
import pathlib
import sys

GRAFANA_VARS = {
    "$__rate_interval": "1m",
    '=~"$application"': '=~".+"',
    '=~"$instance"': '=~".+"',
}


def main(dashboards: str, out: str) -> int:
    rules, uids, problems = [], set(), []
    for path in sorted(pathlib.Path(dashboards).glob("*.json")):
        board = json.loads(path.read_text())
        if board["uid"] in uids:
            problems.append(f"{path.name}: duplicate uid {board['uid']}")
        uids.add(board["uid"])
        for panel in board["panels"]:
            for target in panel.get("targets", []):
                if target["datasource"].get("uid") != "prometheus":
                    problems.append(f"{path.name} / {panel['title']}: not the provisioned datasource")
                expr = target["expr"]
                for var, value in GRAFANA_VARS.items():
                    expr = expr.replace(var, value)
                if "$" in expr:
                    problems.append(f"{path.name} / {panel['title']}: unknown variable in {expr}")
                rules.append({"record": f"dashboard:query:{len(rules)}", "expr": expr})
    pathlib.Path(out).write_text(json.dumps({"groups": [{"name": "dashboards", "rules": rules}]}))
    for p in problems:
        print("ERROR", p)
    print(f"{len(rules)} queries from {len(uids)} dashboards")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
