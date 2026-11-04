import subprocess, re, time, threading, queue, json, zipfile, glob
p = subprocess.Popen(["java", "-Xmx5G", "-jar", "server.jar", "nogui"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
q = queue.Queue(); log = open("full.log", "w")
threading.Thread(target=lambda: [q.put(l) for l in p.stdout], daemon=True).start()
bad = []
BAD = re.compile(r"(?i)\b(error|exception|failed|couldn't|could not|unknown|invalid|missing|not found|warn)\b")
def pump(until=None, t=60):
    end = time.time() + t
    while time.time() < end:
        try: l = q.get(timeout=1)
        except queue.Empty: continue
        log.write(l); print(l, end="")
        if BAD.search(l) and "Soulbound" not in l[:0]: bad.append(l.strip()[-400:])
        if until and re.search(until, l): return l
    return None
def cmd(c, until=None, t=30):
    p.stdin.write(c + "\n"); p.stdin.flush(); return pump(until, t)
print("ready:", pump(r"Done \(", 1500))
startup = list(bad); bad.clear()
cmd("datapack list", r"data pack", 15)
cmd("reload", r"(?i)reload|done", 120)
reload_issues = list(bad); bad.clear()
# generate the middle of Moloch's realm and look for every structure and biome
cmd("execute in sacrifice:sacrifice run forceload add -128 -128 127 127", r"(?i)chunk|forceload", 600)
pump(None, 120)
gen_issues = list(bad); bad.clear()
z = zipfile.ZipFile(glob.glob("world/datapacks/sacrifice*.zip")[0])
names = z.namelist()
found = {}
for n in sorted(x for x in names if x.startswith("data/sacrifice/worldgen/structure/") and x.endswith(".json")):
    s = n.split("/")[-1][:-5]
    r = cmd(f"execute in sacrifice:sacrifice positioned 0 100 0 run locate structure sacrifice:{s}", r"(?i)nearest|could not|unknown|error|invalid", 180)
    found["structure " + s] = (r or "no reply").strip()[-160:]
for n in sorted(x for x in names if x.startswith("data/sacrifice/worldgen/biome/") and x.endswith(".json")):
    b = n.split("/")[-1][:-5]
    r = cmd(f"execute in sacrifice:sacrifice positioned 0 100 0 run locate biome sacrifice:{b}", r"(?i)nearest|could not|unknown|error|invalid", 180)
    found["biome " + b] = (r or "no reply").strip()[-160:]
locate_issues = list(bad); bad.clear()
cmd("scoreboard players get #hunger sacrifice.offer", r"(?i)has|none|unknown", 10)
cmd("execute in sacrifice:sacrifice run summon item 0 131 0 {Item:{id:\"minecraft:redstone\",count:5}}", None, 3)
pump(None, 4)
cmd("scoreboard players get #hunger sacrifice.offer", r"(?i)has|none|unknown", 10)
cmd("function sacrifice:spawn_tick", r"(?i)function|unknown|error", 10)
cmd("datapack list enabled", r"data pack", 10)
cmd("moloch", None, 5); cmd("sb selftest", r"(?i)selftest", 30)
cmd("execute in minecraft:the_end run forceload add -64 -64 63 63", r"(?i)chunk|forceload", 300)
pump(None, 60)
late = list(bad); bad.clear()
cmd("stop", None, 5); p.wait(timeout=600)
pump(None, 5)
def dedup(l):
    out = []
    for x in l:
        k = re.sub(r"^\[[^\]]*\]\s*", "", x)
        if k not in out: out.append(k)
    return out
report = {"startup": dedup(startup), "reload": dedup(reload_issues), "generation": dedup(gen_issues), "locate": found, "locate_issues": dedup(locate_issues), "late": dedup(late + bad)}
json.dump(report, open("report.json", "w"), indent=1)
for k, v in report.items():
    txt = json.dumps(v)[:3800]
    print(f"::notice title={k}::{txt}")
