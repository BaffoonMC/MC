import re
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
r = cmd("execute in sacrifice:sacrifice positioned 1500 100 1500 run locate structure sacrifice:bastion", r"(?i)nearest|could not|unknown|error|invalid", 180)
found["bastion from 1500,1500"] = (r or "no reply").strip()[-160:]
m = re.search(r"\[(-?\d+), ~, (-?\d+)\]", r or "")
if m:
    bx, bz = int(m.group(1)), int(m.group(2))
    cmd(f"execute in sacrifice:sacrifice run forceload add {bx - 96} {bz - 96} {bx + 96} {bz + 96}", r"(?i)chunk|forceload|too many", 300)
    pump(None, 20)
    r = cmd(f"execute in sacrifice:sacrifice run place structure sacrifice:bastion {bx} 100 {bz}", r"(?i)generated|failed|invalid|unknown|error|could not", 240)
    found["bastion place"] = (r or "no reply").strip()[-160:]
    pump(None, 10)
    for what in ["piglin", "piglin_brute", "hoglin"]:
        r = cmd(f"execute in sacrifice:sacrifice positioned {bx} 100 {bz} if entity @e[type=minecraft:{what},distance=..140]", r"(?i)passed|failed|error|unknown", 20)
        found["bastion " + what] = (r or "no reply").strip()[-80:]
    for blk in ["gilded_blackstone", "polished_blackstone_bricks"]:
        r = cmd(f"execute in sacrifice:sacrifice store result score #b sacrifice.offer run fill {bx - 15} 60 {bz - 15} {bx + 15} 90 {bz + 15} minecraft:{blk} replace minecraft:{blk}", r"(?i)filled|no blocks|error|unknown|could not|too many", 30)
        found["bastion blocks " + blk] = (r or "no reply").strip()[-100:]
locate_issues = list(bad); bad.clear()
cmd("scoreboard players get #hunger sacrifice.offer", r"(?i)has|none|unknown", 10)
cmd("execute in sacrifice:sacrifice run summon item 0 131 0 {Item:{id:\"minecraft:redstone\",count:5}}", None, 3)
pump(None, 4)
cmd("scoreboard players get #hunger sacrifice.offer", r"(?i)has|none|unknown", 10)
cmd("function sacrifice:spawn_tick", r"(?i)function|unknown|error", 10)
cmd("datapack list enabled", r"data pack", 10)
for t in ["tree_of_life", "tree_of_knowledge", "cloud_willow"]:
    r = cmd(f"execute in minecraft:the_end positioned 0 100 0 run locate structure heaven:{t}", r"(?i)nearest|could not|unknown|error|invalid", 180)
    found["heaven " + t] = (r or "no reply").strip()[-160:]
cmd("execute in minecraft:the_end run forceload add 1200 1200 1260 1260", r"(?i)chunk|forceload", 120)
for i, t in enumerate(["tree_of_life_1", "tree_of_knowledge_2", "cloud_willow_3"]):
    r = cmd(f"execute in minecraft:the_end run place template heaven:eden/{t} {1200 + i * 20} 150 1200", r"(?i)placed|failed|invalid|unknown|error|not", 60)
    found["place " + t] = (r or "no reply").strip()[-160:]

r = cmd("execute in minecraft:overworld positioned 0 64 0 run locate structure minecraft:stronghold", r"(?i)nearest|could not|unknown|error|invalid", 240)
found["stronghold locate"] = (r or "no reply").strip()[-160:]
cmd("execute in minecraft:overworld run forceload add 2000 2000 2079 2063", r"(?i)chunk|forceload", 120)
r = cmd("execute in minecraft:overworld run place template minecraft:brimstone/stronghold 2000 20 2000", r"(?i)placed|failed|invalid|unknown|error|not", 60)
found["stronghold place"] = (r or "no reply").strip()[-160:]
for what, pos in [("ceiling", "2022 38 2022 minecraft:chiseled_sulfur"), ("chain", "2022 37 2022 minecraft:iron_chain"), ("lantern", "2022 36 2022 minecraft:soul_lantern"), ("frame", "2020 27 2021 minecraft:end_portal_frame"),
                  ("dig hole", "2044 25 2022 minecraft:air"), ("dig rail", "2045 25 2022 minecraft:rail"), ("dig sulfur", "2045 24 2022 minecraft:sulfur"),
                  ("dig cinnabar", "2051 24 2023 minecraft:cinnabar"), ("dig tuff", "2045 24 2020 minecraft:tuff"), ("dig post", "2047 25 2021 minecraft:spruce_fence"),
                  ("dig scaffolding", "2070 25 2016 minecraft:scaffolding")]:
    r = cmd(f"execute in minecraft:overworld if block {pos}", r"(?i)passed|failed|error|unknown", 20)
    found["stronghold " + what] = (r or "no reply").strip()[-100:]
r = cmd("execute in minecraft:overworld if entity @e[type=minecraft:chest_minecart,x=2061.5,y=25,z=2022.5,distance=..3]", r"(?i)passed|failed|error|unknown", 20)
found["dig ore cart"] = (r or "no reply").strip()[-100:]
r = cmd("execute in minecraft:overworld run data get entity @e[type=minecraft:chest_minecart,limit=1,x=2061.5,y=25,z=2022.5,distance=..3] LootTable", r"(?i)has the following|found no|error|unknown|no entity", 20)
found["dig ore cart loot"] = (r or "no reply").strip()[-140:]

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
