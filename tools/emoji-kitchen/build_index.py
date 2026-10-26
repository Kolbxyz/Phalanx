# Builds app/src/main/assets/emoji_kitchen.txt from emoji-kitchen-backend metadata.json:
#   curl -LO https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json
#   python3 tools/emoji-kitchen/build_index.py metadata.json app/src/main/assets/emoji_kitchen.txt
# Format: first line "D <date>...", then "<cp> <cp> <date index>[!]", the two codepoints sorted,
# "!" when the image is stored under the reverse order. Only the latest image of each pair is kept.
import json, sys
m = json.load(open(sys.argv[1]))
pairs = {}
for v in m['data'].values():
    for lst in v['combinations'].values():
        for c in lst:
            if not c['isLatest']: continue
            l, r = c['leftEmojiCodepoint'], c['rightEmojiCodepoint']
            key = tuple(sorted((l, r)))
            pairs[key] = (l, r, c['date'])
dates = sorted({p[2] for p in pairs.values()})
di = {d: i for i, d in enumerate(dates)}
out = ["D " + " ".join(dates)]
for (a, b), (l, r, d) in sorted(pairs.items()):
    # "!" marks that the server stores the pair in the other order.
    out.append(f"{a} {b} {di[d]}{'!' if l != a else ''}")
open(sys.argv[2], 'w').write("\n".join(out) + "\n")
