#!/usr/bin/env python3
"""Renders the README's promo images: a dark stage, a tinted oval and three phone frames.

The screenshots in docs/assets/shots come from a throwaway account holding placeholder chats, so
no real conversation is ever shown. Run from the repository root (needs Pillow):

    python3 tools/promo/render.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SCALE = 2  # draw at twice the size, then downsample, for clean anti-aliased edges
W, H = 1600, 1000
STAGE = (19, 18, 16)
SHOTS = Path("docs/assets/shots")
OUT = Path("docs/assets/mockups")

# Outer oval colour, inner highlight colour.
TINTS = {
    "gold": ((150, 120, 50), (228, 200, 130)),
    "violet": ((110, 85, 190), (196, 176, 245)),
}


def s(v):
    return int(round(v * SCALE))


def stage(tint):
    outer, inner = TINTS[tint]
    img = Image.new("RGB", (s(W), s(H)), STAGE)
    d = ImageDraw.Draw(img)
    d.ellipse([s(210), s(58), s(1510), s(962)], fill=outer)
    d.ellipse([s(430), s(250), s(1250), s(960)], fill=inner)
    return img.convert("RGBA")


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=255)
    return m


def phone(shot, width):
    """A phone frame around `shot`, `width` px wide at output scale."""
    screen_w = s(width - 24)
    src = Image.open(SHOTS / shot).convert("RGB")
    screen_h = int(screen_w * src.height / src.width)
    screen = src.resize((screen_w, screen_h), Image.LANCZOS)
    bezel = s(12)
    fw, fh = screen_w + 2 * bezel, screen_h + 2 * bezel
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle([0, 0, fw - 1, fh - 1], s(64), fill=(28, 27, 24), outline=(78, 72, 60), width=s(3))
    frame.paste(screen, (bezel, bezel), rounded_mask(screen.size, s(52)))
    cx = fw // 2
    d.ellipse([cx - s(9), bezel + s(14), cx + s(9), bezel + s(32)], fill=(8, 8, 10))
    return frame


def place(canvas, device, center, angle=0):
    if angle:
        device = device.rotate(angle, Image.BICUBIC, expand=True)
    alpha = device.getchannel("A")
    shadow = Image.new("RGBA", device.size, (0, 0, 0, 0))
    shadow.putalpha(alpha.point(lambda a: a * 0.7))
    pad = s(60)
    blurred = Image.new("RGBA", (device.width + 2 * pad, device.height + 2 * pad), (0, 0, 0, 0))
    blurred.paste(shadow, (pad, pad))
    blurred = blurred.filter(ImageFilter.GaussianBlur(s(22)))
    x, y = s(center[0]) - device.width // 2, s(center[1]) - device.height // 2
    canvas.alpha_composite(blurred, (x - pad, y - pad + s(18)))
    canvas.alpha_composite(device, (x, y))


def trio(tint, left, middle, right, name):
    c = stage(tint)
    place(c, phone(left, 380), (390, 560), 22)
    place(c, phone(right, 380), (1210, 560), -22)
    place(c, phone(middle, 400), (800, 515), 0)
    out = c.convert("RGB").resize((W, H), Image.LANCZOS)
    out.save(OUT / name, quality=88, optimize=True, progressive=True)
    print(OUT / name)


def render():
    OUT.mkdir(parents=True, exist_ok=True)
    # Messaging: a group chat, the chat list and a message's actions.
    trio("gold", "chat.jpg", "chats.jpg", "message-actions.jpg", "promo-messaging.jpg")
    # Privacy: the privacy switches, settings and the on-device AI models.
    trio("violet", "privacy.jpg", "settings.jpg", "ai.jpg", "promo-privacy.jpg")


if __name__ == "__main__":
    render()
