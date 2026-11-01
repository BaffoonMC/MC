import subprocess, re, time, threading, queue
p = subprocess.Popen(["java", "-Xmx3G", "-jar", "server.jar", "nogui"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
q = queue.Queue()
threading.Thread(target=lambda: [q.put(l) for l in p.stdout], daemon=True).start()
def wait_for(pat, t):
    end = time.time() + t
    while time.time() < end:
        try: l = q.get(timeout=1)
        except queue.Empty: continue
        print(l, end="")
        if re.search(pat, l): return l
hooks = []
def wait_for(pat, t, keep=hooks):
    end = time.time() + t
    while time.time() < end:
        try: l = q.get(timeout=1)
        except queue.Empty: continue
        print(l, end="")
        if "God's island" in l or "GriefPrevention" in l and ("Enabling" in l or "rror" in l): keep.append(l.strip()[-240:])
        if re.search(pat, l): return l
print("ready:", wait_for(r"Done \(", 900))
print("::notice title=island-claims::" + (" | ".join(hooks)[:3500] or "nothing logged"))
p.stdin.write("sb dumpitems\n"); p.stdin.flush()
print("dump:", wait_for(r"Dumped|Couldn't|nknown", 120))
p.stdin.write("sb selftest\n"); p.stdin.flush()
r2 = wait_for(r"selftest|nknown|Exception", 60)
print("::notice title=selftest::" + (r2 or "no reply").strip())
p.stdin.write("forceload add 0 0\n"); p.stdin.flush(); time.sleep(3)
p.stdin.write("sb world brimstone world 0.5 -60 0.5\n"); p.stdin.flush()
got = []
end = time.time() + 20
while time.time() < end:
    try: l = q.get(timeout=1)
    except queue.Empty: continue
    print(l, end="")
    if re.search(r"(?i)brim|hitbox|exception|error|unknown|usage|console", l): got.append(l.strip()[-300:])
print("::notice title=brimstone::" + (" | ".join(got)[:3500] or "no reply"))
p.stdin.write("stop\n"); p.stdin.flush(); p.wait(timeout=300)
