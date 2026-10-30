#!/usr/bin/env python3
import math
import os
from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = "/Users/alptug/.gemini/antigravity/scratch/cinetv-launcher"
DOCS_MEDIA = os.path.join(REPO_ROOT, "docs/media")
FONTS = {
    "bold": os.path.join(REPO_ROOT, "launcher/assets/fonts/Poppins-Bold.ttf"),
    "medium": os.path.join(REPO_ROOT, "launcher/assets/fonts/Poppins-Medium.ttf"),
    "black": os.path.join(REPO_ROOT, "launcher/assets/fonts/Poppins-Black.ttf")
}

def font(style, size):
    return ImageFont.truetype(FONTS.get(style, FONTS["medium"]), size)

def round_rect(draw, bbox, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(bbox, radius=radius, fill=fill, outline=outline, width=width)

def draw_star(draw, cx, cy, r_outer, r_inner, fill):
    points = []
    for i in range(8):
        angle = i * (math.pi / 4) - math.pi / 2
        r = r_outer if i % 2 == 0 else r_inner
        points.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(points, fill=fill)

def draw_play_triangle(draw, cx, cy, size, fill):
    points = [
        (cx - size * 0.4, cy - size * 0.5),
        (cx - size * 0.4, cy + size * 0.5),
        (cx + size * 0.6, cy)
    ]
    draw.polygon(points, fill=fill)

# -------------------------------------------------------------
# 1. NUVIO HOME MEDIA HUB (docs/media/nuvio_home.png)
# -------------------------------------------------------------
def generate_nuvio_home():
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (10, 12, 18, 255))
    d = ImageDraw.Draw(im)

    # Ambient backdrop gradient
    for y in range(h):
        r = int(14 + (y / h) * 10)
        g = int(16 + (y / h) * 12)
        b = int(24 + (y / h) * 18)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Top Brand Header
    round_rect(d, [60, 40, 1860, 110], 20, fill=(25, 30, 44, 200), outline=(50, 60, 85, 255), width=1)
    d.text((90, 56), "NUVIO", font=font("black", 34), fill=(245, 158, 11))
    d.text((220, 64), "Bagimsiz Ev Sinemasi Medya Merkezi & Oynatici Koprusu", font=font("medium", 20), fill=(203, 213, 225))

    # Search bar on right
    round_rect(d, [1360, 52, 1830, 98], 14, fill=(15, 18, 26, 220), outline=(70, 80, 105, 255), width=1)
    d.text((1390, 64), "Kutuphanede Ara...", font=font("medium", 16), fill=(148, 163, 184))

    # Top Hero Banner (Curated Masterpiece)
    round_rect(d, [60, 140, 1860, 520], 28, fill=(18, 22, 34, 240), outline=(245, 158, 11, 120), width=2)

    d.text((110, 185), "ALPTUG DAG KURASYONU · SECKIN SINEMA ARSIVI", font=font("bold", 17), fill=(245, 158, 11))
    d.text((110, 225), "2001: A Space Odyssey", font=font("black", 52), fill=(255, 255, 255))
    d.text((110, 305), "Stanley Kubrick  ·  4K Ultra HD  ·  Dolby Atmos  ·  1968", font=font("medium", 21), fill=(148, 163, 184))
    
    synopsis = (
        "Insanligin evrimsel safagindan uzayin sonsuz sessizligine uzanan felsefi basyapit.\n"
        "CineTV Baslatici Koprusu uzerinden tek dokunusla kayipsiz 4K HDR oynaticiya aktarilir."
    )
    d.text((110, 355), synopsis, font=font("medium", 18), fill=(226, 232, 240), spacing=8)

    # Action buttons
    round_rect(d, [110, 435, 340, 490], 16, fill=(245, 158, 11), outline=None)
    draw_play_triangle(d, 150, 462, 16, (15, 23, 42))
    d.text((175, 450), "Hemen Baslat", font=font("bold", 19), fill=(15, 23, 42))

    round_rect(d, [360, 435, 600, 490], 16, fill=(35, 42, 60, 200), outline=(70, 80, 110), width=1)
    d.text((400, 450), "Detayli Incele", font=font("medium", 19), fill=(255, 255, 255))

    # Shelf: Kategoriler ve Koleksiyonlar
    d.text((60, 555), "Kutuphane Raflari & Film Koleksiyonlari", font=font("bold", 25), fill=(255, 255, 255))
    shelves = [
        ("Criterion Seckisi", "48 Film", (225, 29, 72), "Seven Samurai"),
        ("Bilimkurgu", "64 Film", (14, 165, 233), "Blade Runner"),
        ("Kara Film", "36 Film", (168, 85, 247), "Chinatown"),
        ("4K Dolby Vision", "112 Film", (245, 158, 11), "Apocalypse Now"),
        ("Dunya Sinemasi", "85 Film", (16, 185, 129), "Stalker"),
        ("Klasik Koleksiyon", "42 Film", (239, 68, 68), "Casablanca")
    ]
    card_w = 280
    card_h = 390
    for idx, (title, count, col, sample) in enumerate(shelves):
        cx = 60 + idx * (card_w + 24)
        cy = 610
        round_rect(d, [cx, cy, cx + card_w, cy + card_h], 20, fill=(20, 25, 38, 240), outline=(45, 55, 80, 255), width=1)
        # Poster mockup block
        round_rect(d, [cx + 12, cy + 12, cx + card_w - 12, cy + 295], 14, fill=(col[0]//5, col[1]//5, col[2]//5, 255), outline=col, width=1)
        # 4K Badge
        round_rect(d, [cx + 24, cy + 24, cx + 75, cy + 50], 8, fill=col)
        d.text((cx + 34, cy + 28), "4K", font=font("bold", 13), fill=(255, 255, 255))
        
        # Sample movie title on poster
        d.text((cx + 24, cy + 245), sample, font=font("bold", 17), fill=(255, 255, 255))
        d.text((cx + 16, cy + 315), title, font=font("bold", 18), fill=(255, 255, 255))
        d.text((cx + 16, cy + 345), count + " · Yerel Medya", font=font("medium", 14), fill=(148, 163, 184))

    # Bottom legal architecture note
    round_rect(d, [60, 1020, 1860, 1060], 12, fill=(15, 20, 30, 220), outline=(40, 50, 70), width=1)
    d.text((80, 1029), "CineTV Acik Kaynak Sistem Baslaticisi — Sifir Icerik Barindirma · Standart Android Intent Medya Koprusu", font=font("medium", 14), fill=(148, 163, 184))

    out = os.path.join(DOCS_MEDIA, "nuvio_home.png")
    im.save(out, "PNG")
    print("Saved:", out)

# -------------------------------------------------------------
# 2. ROULETTE WHEEL VIEW (docs/media/roulette_wheel.png)
# -------------------------------------------------------------
def generate_roulette_view():
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (10, 11, 16, 255))
    d = ImageDraw.Draw(im)

    # Ambient theater background
    for y in range(h):
        r = int(12 + (y / h) * 16)
        g = int(10 + (y / h) * 8)
        b = int(22 + (y / h) * 20)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Title header
    d.text((120, 56), "SİNEFİL FİLM ÖNERİ RULETİ (TV & MOBİL EŞ ZAMANLI)", font=font("bold", 20), fill=(245, 158, 11))
    d.text((120, 92), "Bu Akşam Hangi Filmi İzleyelim?", font=font("black", 46), fill=(255, 255, 255))
    d.text((120, 155), "Herkes kendi telefonundan 'Oyunlar' sekmesine girip bir film fırlatır. Çark döner ve kazanan film taçlandırılır!", font=font("medium", 19), fill=(148, 163, 184))

    # Left: Photorealistic Wheel (Diameter ~620px)
    cx, cy = 520, 590
    R = 285
    rim_w = 26

    # Titanium rim
    d.ellipse([cx - R - rim_w, cy - R - rim_w, cx + R + rim_w, cy + R + rim_w], fill=(30, 41, 59, 255), outline=(217, 119, 6, 255), width=4)
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(15, 23, 42, 255))

    slices = [
        ("Taxi Driver", (136, 19, 55)),
        ("Interstellar", (30, 58, 138)),
        ("Pulp Fiction", (6, 95, 70)),
        ("Godfather", (180, 83, 9)),
        ("Fight Club", (88, 28, 135)),
        ("The Matrix", (15, 118, 110)),
        ("Inception", (153, 27, 27)),
        ("Whiplash", (49, 46, 129))
    ]
    n = len(slices)
    step = 360 / n
    for i, (title, color) in enumerate(slices):
        sa = i * step
        ea = sa + step
        d.pieslice([cx - R, cy - R, cx + R, cy + R], sa, ea, fill=color, outline=(255, 255, 255, 80), width=2)
        # Radial text label
        mid_ang = math.radians(sa + step / 2)
        tx = cx + (R * 0.62) * math.cos(mid_ang)
        ty = cy + (R * 0.62) * math.sin(mid_ang)
        d.text((tx - 32, ty - 10), title, font=font("bold", 15), fill=(255, 255, 255))

    # Golden rivets (pegs)
    for i in range(n):
        ang = math.radians(i * step)
        px = cx + (R + rim_w / 2) * math.cos(ang)
        py = cy + (R + rim_w / 2) * math.sin(ang)
        d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=(251, 191, 36), outline=(120, 53, 15), width=2)

    # Polished brass hub with specular center
    hub_r = 75
    d.ellipse([cx - hub_r, cy - hub_r, cx + hub_r, cy + hub_r], fill=(217, 119, 6), outline=(254, 243, 199), width=3)
    d.ellipse([cx - hub_r * 0.7, cy - hub_r * 0.7, cx + hub_r * 0.7, cy + hub_r * 0.7], fill=(245, 158, 11))
    # Vector golden star
    draw_star(d, cx, cy, 26, 11, (255, 255, 255))

    # Top indicator needle (pointer)
    top_y = cy - R - rim_w
    d.polygon([(cx - 14, top_y - 8), (cx + 14, top_y - 8), (cx, top_y + 36)], fill=(245, 158, 11), outline=(254, 243, 199))
    d.ellipse([cx - 8, top_y - 8, cx + 8, top_y + 8], fill=(254, 243, 199))

    # Right: Modal Card & Winner Card
    rx = 980
    round_rect(d, [rx, 240, rx + 820, 890], 28, fill=(20, 24, 36, 230), outline=(245, 158, 11, 120), width=2)

    # Winner Card
    round_rect(d, [rx + 36, 280, rx + 784, 460], 20, fill=(245, 158, 11, 35), outline=(245, 158, 11, 200), width=2)
    d.text((rx + 60, 305), "BU AKŞAMIN KAZANAN FİLMİ (PREMİYER SEÇİMİ)", font=font("bold", 15), fill=(251, 191, 36))
    d.text((rx + 60, 335), "Taxi Driver (1976)", font=font("black", 38), fill=(255, 255, 255))
    d.text((rx + 60, 395), "Öneren: Alptuğ  ·  Martin Scorsese  ·  Robert De Niro", font=font("medium", 18), fill=(203, 213, 225))

    # Suggestions pool list
    d.text((rx + 36, 495), "Oturma Odası Havuzundaki Öneriler:", font=font("bold", 20), fill=(255, 255, 255))
    suggested = [
        ("Taxi Driver", "Alptuğ", (245, 158, 11)),
        ("Interstellar", "Gamze", (56, 189, 248)),
        ("Pulp Fiction", "Misafir 1", (239, 68, 68)),
        ("The Godfather", "Misafir 2", (16, 185, 129))
    ]
    for s_idx, (m_title, user, col) in enumerate(suggested):
        sy = 540 + s_idx * 58
        round_rect(d, [rx + 36, sy, rx + 784, sy + 48], 14, fill=(30, 38, 56, 220), outline=(50, 65, 95), width=1)
        d.text((rx + 56, sy + 12), "• " + m_title, font=font("bold", 17), fill=(255, 255, 255))
        d.text((rx + 640, sy + 14), "(" + user + ")", font=font("medium", 15), fill=col)

    # Controls row
    round_rect(d, [rx + 36, 800, rx + 520, 860], 16, fill=(139, 92, 246), outline=None)
    d.text((rx + 200, 818), "Çarkı Çevir", font=font("bold", 20), fill=(255, 255, 255))

    round_rect(d, [rx + 540, 800, rx + 784, 860], 16, fill=(35, 42, 60, 200), outline=(70, 80, 110), width=1)
    d.text((rx + 610, 818), "Temizle", font=font("medium", 20), fill=(203, 213, 225))

    out = os.path.join(DOCS_MEDIA, "roulette_wheel.png")
    im.save(out, "PNG")
    print("Saved:", out)

# -------------------------------------------------------------
# 3. CINEPARTY: 35MM CELLULOID SNAKE & 10-QUESTION QUIZ ARENA
# -------------------------------------------------------------
def generate_cineparty_games():
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (8, 9, 14, 255))
    d = ImageDraw.Draw(im)

    for y in range(h):
        r = int(10 + (y / h) * 14)
        g = int(14 + (y / h) * 18)
        b = int(22 + (y / h) * 26)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Header
    d.text((100, 48), "CİNEPARTY: OTURMA ODASI İNTERAKTİF SİNEMA OYUNLARI", font=font("bold", 19), fill=(16, 185, 129))
    d.text((100, 84), "35mm Selüloit Yılanı ve 10 Soruluk Sinefil Bilmece Arenası", font=font("black", 42), fill=(255, 255, 255))

    # Left: 35mm Celluloid Snake Game Card
    lx = 100
    round_rect(d, [lx, 165, lx + 830, 995], 28, fill=(16, 20, 30, 235), outline=(16, 185, 129, 160), width=2)
    d.text((lx + 40, 198), "35MM SELÜLOİT SİNEFİL YILANI (GYRO EĞİM KONTROLLÜ)", font=font("bold", 17), fill=(16, 185, 129))
    d.text((lx + 40, 232), "Telefonunuzu sağa-sola, öne-arkaya eğerek yönetin; 3D film DVD'lerini yedikçe uzayın!", font=font("medium", 15), fill=(148, 163, 184))

    # HUD Bar
    round_rect(d, [lx + 40, 275, lx + 790, 325], 12, fill=(24, 30, 44), outline=(45, 58, 85), width=1)
    d.text((lx + 60, 288), "KARE (SKOR): 180", font=font("bold", 16), fill=(16, 185, 129))
    d.text((lx + 360, 288), "REKOR: 420", font=font("bold", 16), fill=(245, 158, 11))
    d.text((lx + 580, 288), "HEDEF DVD: Interstellar", font=font("medium", 15), fill=(56, 189, 248))

    # 3D Cinema Board (lx + 175, 350, 480x480)
    bx, by, b_size = lx + 175, 350, 480
    round_rect(d, [bx, by, bx + b_size, by + b_size], 22, fill=(9, 12, 18), outline=(245, 158, 11, 70), width=2)

    # Subtle projector lens vignette & grid lines
    grid_n = 12
    cell = b_size / grid_n
    for c in range(1, grid_n):
        d.line([(bx + c * cell, by), (bx + c * cell, by + b_size)], fill=(245, 158, 11, 15))
        d.line([(bx, by + c * cell), (bx + b_size, by + c * cell)], fill=(245, 158, 11, 15))

    # Food: Photorealistic 3D Miniature Keepcase DVD (Interstellar)
    fx = bx + 8 * cell + 4
    fy = by + 3 * cell + 4
    dvd_w, dvd_h = cell - 8, cell - 6
    # 3D drop shadow
    round_rect(d, [fx + 4, fy + 8, fx + dvd_w + 4, fy + dvd_h + 8], 6, fill=(0, 0, 0, 160))
    # Ambient halo
    round_rect(d, [fx - 6, fy - 6, fx + dvd_w + 6, fy + dvd_h + 6], 10, fill=(2, 132, 199, 45))
    # Outer keepcase plastic
    round_rect(d, [fx, fy, fx + dvd_w, fy + dvd_h], 6, fill=(10, 14, 23), outline=(56, 189, 248, 200), width=1)
    # Cover Art: Cyan galaxy / nebula
    round_rect(d, [fx + 3, fy + 3, fx + dvd_w - 3, fy + dvd_h - 3], 4, fill=(2, 132, 199))
    d.text((fx + 6, fy + 12), "INT", font=font("black", 12), fill=(255, 255, 255))
    # Spine highlight (gold)
    d.rectangle([fx + 3, fy + 3, fx + 6, fy + dvd_h - 3], fill=(253, 230, 138))
    # Diagonal cellophane reflection streak
    d.line([(fx + 3, fy + 4), (fx + dvd_w - 4, fy + dvd_h - 4)], fill=(255, 255, 255, 140), width=2)

    # Snake: 3D Cine Camera Lens Head + 35mm Celluloid Segments
    snake_coords = [(6, 5), (5, 5), (4, 5), (3, 5), (3, 6), (3, 7), (4, 7), (5, 7), (6, 7)]
    for s_idx, (sx, sy) in enumerate(snake_coords):
        px = bx + sx * cell + 3
        py = by + sy * cell + 3
        sw, sh = cell - 6, cell - 6
        fade = max(0.4, 1.0 - (s_idx / len(snake_coords)) * 0.5)

        if s_idx == 0:
            # 3D Head: Cinema Projector Lens Barrel
            round_rect(d, [px + 3, py + 5, px + sw + 3, py + sh + 5], int(sw // 2), fill=(0, 0, 0, 160))
            d.ellipse([px, py, px + sw, py + sh], fill=(30, 41, 59), outline=(245, 158, 11), width=2)
            d.ellipse([px + 4, py + 4, px + sw - 4, py + sh - 4], fill=(16, 185, 129), outline=(4, 120, 87), width=1)
            # Tracking Eye looking toward the DVD
            d.ellipse([px + sw - 14, py + 8, px + sw - 6, py + 16], fill=(255, 255, 255))
            d.ellipse([px + sw - 12, py + 9, px + sw - 7, py + 14], fill=(15, 23, 42))
            # Lens reflection arc
            d.arc([px + 6, py + 6, px + sw - 6, py + sh - 6], start=210, end=300, fill=(255, 255, 255), width=2)
        else:
            # 3D Celluloid Filmstrip Segment
            round_rect(d, [px + 2, py + 4, px + sw + 2, py + sh + 4], 6, fill=(0, 0, 0, 130))
            round_rect(d, [px, py, px + sw, py + sh], 6, fill=(22, 28, 42), outline=(245, 158, 11, int(fade * 255)), width=1)
            # Center projection window
            round_rect(d, [px + 5, py + 5, px + sw - 5, py + sh - 5], 4, fill=(16, 185, 129, int(fade * 110)))
            # Kodak 35mm Sprocket Perforations (4 holes)
            d.rectangle([px + 4, py + 2, px + 8, py + 4], fill=(248, 250, 252))
            d.rectangle([px + sw - 8, py + 2, px + sw - 4, py + 4], fill=(248, 250, 252))
            d.rectangle([px + 4, py + sh - 4, px + 8, py + sh - 2], fill=(248, 250, 252))
            d.rectangle([px + sw - 8, py + sh - 4, px + sw - 4, py + sh - 2], fill=(248, 250, 252))

    d.text((lx + 235, 850), "Eğim Sensörü Aktif  ·  Dokunmatik D-Pad Destekli", font=font("medium", 15), fill=(148, 163, 184))
    round_rect(d, [lx + 180, 885, lx + 650, 950], 16, fill=(16, 185, 129), outline=None)
    d.text((lx + 335, 905), "Oyunu Başlat", font=font("bold", 20), fill=(10, 20, 15))

    # Right: 10-Question Cinephile Quiz Arena Card
    rx = 990
    round_rect(d, [rx, 165, rx + 830, 995], 28, fill=(16, 20, 30, 235), outline=(245, 158, 11, 160), width=2)
    d.text((rx + 40, 198), "SİNEFİL BİLMECE ARENASI (10 SORU & CANLI YARIŞMA)", font=font("bold", 17), fill=(245, 158, 11))
    d.text((rx + 40, 232), "Ekrana aktör resmi gelir, 10s içinde bilin; kazanan akşamın filmini seçer!", font=font("medium", 15), fill=(148, 163, 184))

    # Question Card with Actor Portrait and Live Buzzer
    round_rect(d, [rx + 40, 275, rx + 790, 620], 20, fill=(22, 28, 44, 230), outline=(245, 158, 11, 120), width=1)
    
    # Top bar: Soru 1 / 10 and Countdown Ring
    d.text((rx + 65, 298), "SORU 1 / 10  ·  AKTÖR TANIMA", font=font("bold", 15), fill=(245, 158, 11))
    round_rect(d, [rx + 680, 292, rx + 765, 328], 10, fill=(239, 68, 68, 40), outline=(239, 68, 68), width=1)
    d.text((rx + 705, 300), "10s", font=font("bold", 17), fill=(248, 113, 113))

    # Clue Text
    d.text((rx + 65, 342), "Bu ünlü aktör kimdir?", font=font("bold", 22), fill=(255, 255, 255))
    d.text((rx + 65, 372), "İpucu: Oppenheimer, Peaky Blinders, Inception", font=font("medium", 15), fill=(148, 163, 184))

    # 3D Actor Portrait Frame (rx + 65, 410, 160x180)
    ax, ay, aw, ah = rx + 65, 410, 160, 180
    round_rect(d, [ax - 2, ay - 2, ax + aw + 2, ay + ah + 2], 12, fill=(10, 14, 22), outline=(245, 158, 11), width=2)
    round_rect(d, [ax, ay, ax + aw, ay + ah], 10, fill=(18, 24, 38))
    d.ellipse([ax + 30, ay + 20, ax + aw - 30, ay + 90], fill=(45, 55, 75))
    d.ellipse([ax + 15, ay + 15, ax + aw - 15, ay + 45], fill=(15, 20, 30))
    d.rectangle([ax + 40, ay + 5, ax + aw - 40, ay + 25], fill=(15, 20, 30))
    d.ellipse([ax + 50, ay + 48, ax + 66, ay + 56], fill=(56, 189, 248))
    d.ellipse([ax + aw - 66, ay + 48, ax + aw - 50, ay + 56], fill=(56, 189, 248))
    d.text((ax + 34, ay + 145), "CILLIAN M.", font=font("bold", 13), fill=(245, 158, 11))

    # Live Living Room Answers Box (rx + 250, 410, 515x180)
    bx2 = rx + 250
    round_rect(d, [bx2, ay, rx + 765, ay + ah], 14, fill=(14, 18, 28), outline=(45, 58, 85), width=1)
    d.text((bx2 + 20, ay + 14), "10s DOLDU · SALONDAKİ CEVAPLAR:", font=font("bold", 13), fill=(16, 185, 129))
    
    # Correct Answer
    round_rect(d, [bx2 + 15, ay + 38, rx + 750, ay + 72], 8, fill=(16, 185, 129, 35), outline=(16, 185, 129), width=1)
    d.text((bx2 + 25, ay + 46), "DOĞRU CEVAP: Cillian Murphy", font=font("bold", 14), fill=(52, 211, 153))

    # What each friend submitted
    d.text((bx2 + 20, ay + 86), "Alptuğ:  “Cillian Murphy”  ✓ DOĞRU (+10p)", font=font("bold", 13), fill=(52, 211, 153))
    d.text((bx2 + 20, ay + 114), "Gamze:  “Tommy Shelby”  ✓ KABUL (+10p)", font=font("bold", 13), fill=(56, 189, 248))
    d.text((bx2 + 20, ay + 142), "Can:  “Leonardo DiCaprio”  ❌ YANLIŞ (0p)", font=font("bold", 13), fill=(248, 113, 113))

    # Leaderboard Section
    d.text((rx + 40, 642), "Canlı Liderlik Tablosu:", font=font("bold", 19), fill=(255, 255, 255))
    players = [
        ("1. Alptuğ", "90 Puan", "Hızlı Yanıt (+20)", (16, 185, 129)),
        ("2. Gamze", "70 Puan", "Doğru (+10)", (56, 189, 248)),
        ("3. Can (Salondaki Dost)", "40 Puan", "Son Yanıt (0)", (245, 158, 11))
    ]
    for p_idx, (p_name, score, bonus, col) in enumerate(players):
        py = 680 + p_idx * 56
        round_rect(d, [rx + 40, py, rx + 790, py + 48], 12, fill=(24, 31, 46), outline=(45, 58, 85), width=1)
        d.text((rx + 60, py + 12), p_name, font=font("bold", 16), fill=(255, 255, 255))
        d.text((rx + 420, py + 14), bonus, font=font("medium", 14), fill=col)
        d.text((rx + 680, py + 12), score, font=font("bold", 16), fill=(255, 255, 255))

    # Final coronation message banner
    round_rect(d, [rx + 40, 875, rx + 790, 945], 16, fill=(245, 158, 11, 35), outline=(245, 158, 11), width=1)
    d.text((rx + 75, 897), "10 Soru Sonunda: “Bugün, kazanan hangi filmi seçerse o izlenir!”", font=font("bold", 17), fill=(251, 191, 36))

    out = os.path.join(DOCS_MEDIA, "cineparty_games.png")
    im.save(out, "PNG")
    print("Saved:", out)

# -------------------------------------------------------------
# 4. CINESUB OVERLAY & AI VISION (docs/media/cinesub_overlay.png)
# -------------------------------------------------------------
def generate_cinesub_overlay():
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (8, 9, 14, 255))
    d = ImageDraw.Draw(im)

    # Ambient deep theater background matching CineTV
    for y in range(h):
        r = int(9 + (y / h) * 9)
        g = int(11 + (y / h) * 11)
        b = int(17 + (y / h) * 15)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Top Header
    d.text((120, 68), "CINESUB · SAHNEYİ DURDURMAYAN İNTERAKTİF KÜLTÜREL ALTYAZI REHBERİ", font=font("bold", 18), fill=(245, 158, 11))
    d.text((120, 106), "Alptuğ Dağ — 'Sessiz' (2023) Canlı Seyir Sahnesi", font=font("black", 38), fill=(255, 255, 255))

    # Widescreen cinematic frame container (16:9 frame at 120, 185, 1140 x 670)
    fx, fy, fw, fh = 120, 185, 1140, 670
    round_rect(d, [fx - 3, fy - 3, fx + fw + 3, fy + fh + 3], 20, fill=(0, 0, 0), outline=(245, 158, 11, 75), width=2)

    # Load actual frame from Alptuğ Dağ's film "Sessiz" (1280x536, 2.39:1 CinemaScope)
    sessiz_path = os.path.join(DOCS_MEDIA, "sessiz_frame.jpg")
    if os.path.isfile(sessiz_path):
        frame_img = Image.open(sessiz_path).convert("RGBA")
        # Scale to fit width fw (1140), calculate CinemaScope height: 1140 * 536 / 1280 = 477 px
        scope_h = int(fw * frame_img.height / frame_img.width)
        frame_img = frame_img.resize((fw, scope_h), Image.Resampling.BICUBIC)
        # Center inside 16:9 viewport with authentic anamorphic letterbox matte bars
        scope_y = fy + (fh - scope_h) // 2
        im.paste(frame_img, (fx, scope_y))
        d = ImageDraw.Draw(im)

    # Top Cinema Watermark Badge
    round_rect(d, [fx + 24, fy + 20, fx + 250, fy + 56], 10, fill=(10, 14, 24, 220), outline=(245, 158, 11, 140), width=1)
    d.text((fx + 38, fy + 30), "4K MASTER · SESSİZ (2023)", font=font("bold", 12), fill=(251, 191, 36))

    # Real Subtitle Bar from the Film at bottom center
    sub_w = 760
    sub_x = fx + (fw - sub_w) // 2
    sub_y = fy + fh - 65
    round_rect(d, [sub_x, sub_y, sub_x + sub_w, sub_y + 48], 12, fill=(0, 0, 0, 220), outline=(255, 255, 255, 30), width=1)
    d.text((sub_x + 28, sub_y + 12), "“Cellatları dilsizlerden seçerlermiş derler... ama ya ben?”", font=font("medium", 18), fill=(254, 240, 138))

    # Floating CineSub Cultural Note Card (right side: card_x = 1295, card_y = 185, 505 x 670)
    card_x = 1295
    card_y = 185
    card_w = 505
    card_h = 670

    # 3D Liquid Glass Panel matching CineTV UiKit
    round_rect(d, [card_x, card_y, card_x + card_w, card_y + card_h], 26, fill=(11, 14, 22, 248), outline=(245, 158, 11, 200), width=2)

    # Badge Capsule: ✦ CİNESUB KÜLTÜREL REHBER
    round_rect(d, [card_x + 26, card_y + 26, card_x + 280, card_y + 60], 10, fill=(245, 158, 11, 40), outline=(245, 158, 11, 180), width=1)
    d.text((card_x + 38, card_y + 35), "✦ CİNESUB KÜLTÜREL REHBER", font=font("bold", 12), fill=(251, 191, 36))

    # Title & Film Meta
    d.text((card_x + 26, card_y + 80), "Osmanlı Saray Geleneği &\nVicdan Metaforu", font=font("black", 23), fill=(255, 255, 255), spacing=4)
    d.text((card_x + 26, card_y + 148), "Alptuğ Dağ · Sessiz (2023) Kısametraj", font=font("bold", 13), fill=(148, 163, 184))

    # Subtitle Context Highlight Inset
    round_rect(d, [card_x + 24, card_y + 178, card_x + card_w - 24, card_y + 234], 12, fill=(18, 22, 34, 230), outline=(245, 158, 11, 90), width=1)
    d.text((card_x + 36, card_y + 188), "Altyazı Repliği:", font=font("bold", 11), fill=(245, 158, 11))
    d.text((card_x + 36, card_y + 206), "“Cellatları dilsizlerden seçerlermiş derler... ama ya ben?”", font=font("medium", 13), fill=(254, 240, 138))

    # Cultural Explanation Body with Proper Turkish Characters
    card_text = (
        "Alptuğ Dağ'ın yönettiği Sessiz (2023) filminde bu replik,\n"
        "Osmanlı sarayında cellatların dilsizlerden seçilerek\n"
        "kurbanların yalvarışlarını duymalarının engellenmesi\n"
        "tarihsel gerçeğine doğrudan bir göndermedir.\n\n"
        "Karakter, bu tarihi olguyu kendi içsel vicdanı,\n"
        "ahlaki felci ve suskunluğuyla yüzleştiği dramatik bir\n"
        "metafordan geçirir. Minimalist ses tasarımı ve diejetik\n"
        "sessizlik, Robert Bresson ve Nuri Bilge Ceylan sinemasındaki\n"
        "'sessizliğin dramatik ağırlığı' geleneğine saygı duruşudur."
    )
    d.text((card_x + 26, card_y + 252), card_text, font=font("medium", 13.5), fill=(226, 232, 240), spacing=5)

    # Gemini Vision Scene Inspector Inset (bottom of card)
    round_rect(d, [card_x + 22, card_y + 465, card_x + card_w - 22, card_y + 645], 16, fill=(10, 14, 24), outline=(56, 189, 248, 130), width=1)
    round_rect(d, [card_x + 34, card_y + 480, card_x + 285, card_y + 508], 8, fill=(56, 189, 248, 25), outline=(56, 189, 248, 160), width=1)
    d.text((card_x + 44, card_y + 487), "✦ GÖRSEL SAHNE İNCELEMESİ (GEMINI AI)", font=font("bold", 11), fill=(56, 189, 248))
    
    d.text((card_x + 34, card_y + 520), "Sahne Öğesi: Bıçak & Psikolojik Donakalma (Freezing)", font=font("bold", 14.5), fill=(255, 255, 255))
    vision_txt = (
        "Kadrajdaki düşük anahtarlı ışık (low-key) ve bıçak nesnesi,\n"
        "kurbanın intikam arzusu ile ahlaki vicdanı arasındaki çatışmayı\n"
        "chiaroscuro gölge-ışık tezatıyla görselleştirir."
    )
    d.text((card_x + 34, card_y + 555), vision_txt, font=font("medium", 12.5), fill=(148, 163, 184), spacing=4)

    # Bottom notice banner
    round_rect(d, [120, 915, 1800, 975], 14, fill=(14, 18, 28), outline=(45, 55, 75), width=1)
    d.text((150, 935), "Kumandadaki Sarı tuşa veya telefon kumandadaki CineSub düğmesine basıldığında film kesilmeden kenarda süzülür · Sıfır duraklama", font=font("medium", 15), fill=(148, 163, 184))

    out = os.path.join(DOCS_MEDIA, "cinesub_overlay.png")
    im.save(out, "PNG")
    print("Saved:", out)

if __name__ == "__main__":
    generate_roulette_view()
    generate_cineparty_games()
    generate_cinesub_overlay()
