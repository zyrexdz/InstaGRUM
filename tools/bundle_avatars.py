"""One-time asset maintenance: bundles freely licensed portrait photos for fictional profiles.
Images come from Unsplash (unsplash.com/license) and are stored locally; the app never fetches them.
"""
from pathlib import Path
from urllib.request import Request, urlopen
from concurrent.futures import ThreadPoolExecutor

from PIL import Image
from io import BytesIO

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/avatars"
IDS = [
    "photo-1507003211169-0a1dd7228f2d", "photo-1494790108377-be9c29b29330",
    "photo-1500648767791-00dcc994a43e", "photo-1534528741775-53994a69daeb",
    "photo-1519085360753-af0119f7cbe7", "photo-1524504388940-b1c1722653e1",
    "photo-1506794778202-cad84cf45f1d", "photo-1529626455594-4ff0802cfb7e",
    "photo-1539571696357-5a69c17a67c6", "photo-1503342217505-b0a15ec3261c",
    "photo-1521119989659-a83eee488004", "photo-1544005313-94ddf0286df2",
    "photo-1502823403499-6ccfcf4fb453", "photo-1547425260-76bcadfb4f2c",
    "photo-1492562080023-ab3db95bfbce", "photo-1519345182560-3f2917c472ef",
    "photo-1524250502761-1ac6f2e30d43", "photo-1541101767792-f9b2b1c4f127",
    "photo-1508214751196-bcfd4ca60f91", "photo-1489424731084-a5d8b219a5bb",
    "photo-1531123897727-8f129e1688ce", "photo-1517841905240-472988babdf9",
    "photo-1521572267360-ee0c2909d518", "photo-1521737852567-6949f3f9f2b5",
]

def fetch(pair):
    index, pid = pair
    url = f"https://images.unsplash.com/{pid}?auto=format&fit=crop&crop=faces&w=256&h=256&q=80"
    try:
        data = urlopen(Request(url, headers={"User-Agent": "InstaGRUM-assets/1.0"}), timeout=30).read()
        im = Image.open(BytesIO(data)).convert("RGB")
        side = min(im.size)
        im = im.crop(((im.width - side) // 2, (im.height - side) // 2, (im.width + side) // 2, (im.height + side) // 2)).resize((256, 256))
        im.save(OUT / f"{index}.jpg", quality=86, optimize=True)
        return f"{index}.jpg"
    except Exception as error:
        return f"{index}.jpg FAILED {error}"

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        for result in pool.map(fetch, enumerate(IDS)):
            print(result)
