"""Optional asset maintenance, never run by Gradle or the app.
Photos are bundled once under the Unsplash license; no runtime network access.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen, Request
from PIL import Image
from io import BytesIO

ROOT = Path(__file__).resolve().parent.parent
PHOTOS = [
    'photo-1464822759023-fed622ff2c3b',
    'photo-1442512595331-e89e73853f31',
    'photo-1476514525535-07fb3b4ae5f1',
    'photo-1500530855697-b586d89ba3ee',
    'photo-1519501025264-65ba15a82390',
    'photo-1441974231531-c6227db76b6e',
    'photo-1475924156734-496f6cac6ec1',
    'photo-1445116572660-236099ec97a0',
    'photo-1469474968028-56623f02e42e',
]

def download(pair):
    index, photo = pair
    url = f'https://images.unsplash.com/{photo}?auto=format&fit=crop&w=960&q=85'
    with urlopen(Request(url, headers={'User-Agent': 'InstaGRUM-assets/1.0'}), timeout=40) as response:
        image = Image.open(BytesIO(response.read())).convert('RGB')
    target = ROOT / f'app/src/main/assets/photos/{index}.jpg'
    image.save(target, quality=88, optimize=True)
    return f'{target.name}: {image.size}'

if __name__ == '__main__':
    with ThreadPoolExecutor(max_workers=3) as pool:
        for result in pool.map(download, enumerate(PHOTOS)):
            print(result)
