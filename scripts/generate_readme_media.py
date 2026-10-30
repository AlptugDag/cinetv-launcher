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
    d.text((120, 56), "SINEFIL FILM ONERI RULETI (TV & MOBIL ES ZAMANLI)", font=font("bold", 20), fill=(245, 158, 11))
    d.text((120, 92), "Bu Aksam Hangi Filmi Izleyelim?", font=font("black", 46), fill=(255, 255, 255))
    d.text((120, 155), "Herkes kendi telefonundan 'Oyunlar' sekmesine girip bir film firlatir. Cark doner ve kazanan film taclandirilir!", font=font("medium", 19), fill=(148, 163, 184))

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
    d.text((rx + 60, 305), "BU AKSAMIN KAZANAN FILMI (PREMIYER SECIMI)", font=font("bold", 15), fill=(251, 191, 36))
    d.text((rx + 60, 335), "Taxi Driver (1976)", font=font("black", 38), fill=(255, 255, 255))
    d.text((rx + 60, 395), "Oneren: Alptug  ·  Martin Scorsese  ·  Robert De Niro", font=font("medium", 18), fill=(203, 213, 225))

    # Suggestions pool list
    d.text((rx + 36, 495), "Oturma Odasi Havuzundaki Oneriler:", font=font("bold", 20), fill=(255, 255, 255))
    suggested = [
        ("Taxi Driver", "Alptug", (245, 158, 11)),
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
    d.text((rx + 200, 818), "Carki Cevir", font=font("bold", 20), fill=(255, 255, 255))

    round_rect(d, [rx + 540, 800, rx + 784, 860], 16, fill=(35, 42, 60, 200), outline=(70, 80, 110), width=1)
    d.text((rx + 610, 818), "Temizle", font=font("medium", 20), fill=(203, 213, 225))

    out = os.path.join(DOCS_MEDIA, "roulette_wheel.png")
    im.save(out, "PNG")
    print("Saved:", out)

# -------------------------------------------------------------
# 3. 35MM SNAKE & QUIZ ARENA (docs/media/cineparty_games.png)
# -------------------------------------------------------------
def generate_cineparty_games():
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (9, 10, 15, 255))
    d = ImageDraw.Draw(im)

    # Ambient theater background
    for y in range(h):
        r = int(10 + (y / h) * 14)
        g = int(14 + (y / h) * 18)
        b = int(22 + (y / h) * 26)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Header
    d.text((100, 50), "CINEPARTY: OTURMA ODASI INTERAKTIF SINEMA OYUNLARI", font=font("bold", 20), fill=(16, 185, 129))
    d.text((100, 84), "35mm Seluloit Yilani ve 10 Soruluk Sinefil Bilmece Arenasi", font=font("black", 42), fill=(255, 255, 255))

    # Left: 35mm Celluloid Snake Game Card
    lx = 100
    round_rect(d, [lx, 170, lx + 830, 990], 28, fill=(18, 22, 32, 230), outline=(16, 185, 129, 140), width=2)
    d.text((lx + 40, 205), "35MM SELULOIT SINEFIL YILANI (GYRO EGIM KONTROLLU)", font=font("bold", 17), fill=(16, 185, 129))
    d.text((lx + 40, 240), "Telefonunuzu saga-sola, one-arkaya egerek yonetin; klasik film DVD'lerini yedikce uzayin!", font=font("medium", 15), fill=(148, 163, 184))

    # HUD Bar
    round_rect(d, [lx + 40, 285, lx + 790, 335], 12, fill=(25, 32, 48), outline=(45, 58, 85), width=1)
    d.text((lx + 60, 298), "KARE (SKOR): 140", font=font("bold", 16), fill=(16, 185, 129))
    d.text((lx + 420, 298), "REKOR: 420", font=font("bold", 16), fill=(245, 158, 11))
    d.text((lx + 620, 298), "HEDEF: Interstellar", font=font("medium", 15), fill=(56, 189, 248))

    # Snake Game Board (440x440)
    bx, by, b_size = lx + 195, 360, 440
    round_rect(d, [bx, by, bx + b_size, by + b_size], 18, fill=(9, 10, 15), outline=(255, 255, 255, 40), width=2)

    grid_n = 16
    cell = b_size / grid_n
    for c in range(1, grid_n):
        d.line([(bx + c * cell, by), (bx + c * cell, by + b_size)], fill=(255, 255, 255, 12))
        d.line([(bx, by + c * cell), (bx + b_size, by + c * cell)], fill=(255, 255, 255, 12))

    # Food: 3D miniature keepcase DVD
    food_x = bx + 11 * cell
    food_y = by + 5 * cell
    round_rect(d, [food_x + 2, food_y + 1, food_x + cell - 2, food_y + cell - 1], 4, fill=(14, 165, 233), outline=(255, 255, 255, 180), width=1)
    d.line([(food_x + 4, food_y + 3), (food_x + cell - 4, food_y + cell - 3)], fill=(255, 255, 255, 120), width=2)

    # Snake Body: 35mm celluloid frames
    snake_coords = [(8, 8), (7, 8), (6, 8), (5, 8), (5, 9), (5, 10), (5, 11), (4, 11), (3, 11)]
    for s_idx, (sx, sy) in enumerate(snake_coords):
        px = bx + sx * cell
        py = by + sy * cell
        if s_idx == 0:
            d.ellipse([px + 2, py + 2, px + cell - 2, py + cell - 2], fill=(30, 41, 59), outline=(217, 119, 6), width=2)
            d.ellipse([px + 6, py + 6, px + cell - 6, py + cell - 6], fill=(16, 185, 129))
            d.ellipse([px + 10, py + 8, px + 15, py + 13], fill=(255, 255, 255))
        else:
            round_rect(d, [px + 2, py + 2, px + cell - 2, py + cell - 2], 3, fill=(22, 27, 38), outline=(180, 83, 9, 180), width=1)
            d.rectangle([px + 4, py + 3, px + 7, py + 5], fill=(241, 245, 249))
            d.rectangle([px + cell - 7, py + 3, px + cell - 4, py + 5], fill=(241, 245, 249))
            d.rectangle([px + 4, py + cell - 5, px + 7, py + cell - 3], fill=(241, 245, 249))
            d.rectangle([px + cell - 7, py + cell - 5, px + cell - 4, py + cell - 3], fill=(241, 245, 249))
            d.rectangle([px + 6, py + 7, px + cell - 6, py + cell - 7], fill=(16, 185, 129, 90))

    d.text((lx + 235, 825), "Egim Sensoru Aktif  ·  Dokunmatik Yon Tuslari Destekli", font=font("medium", 15), fill=(148, 163, 184))
    round_rect(d, [lx + 180, 865, lx + 650, 930], 16, fill=(16, 185, 129), outline=None)
    d.text((lx + 335, 885), "Oyunu Baslat", font=font("bold", 20), fill=(10, 20, 15))

    # Right: 10-Question Cinephile Quiz Arena Card
    rx = 990
    round_rect(d, [rx, 170, rx + 830, 990], 28, fill=(18, 22, 32, 230), outline=(245, 158, 11, 140), width=2)
    d.text((rx + 40, 205), "SINEFIL BILMECE ARENASI (10 SORU)", font=font("bold", 17), fill=(245, 158, 11))
    d.text((rx + 40, 240), "Coklu telefonla baglanin! Ekrana gelen sahneleri 10s icinde bilin, aksamin filmini siz secin!", font=font("medium", 15), fill=(148, 163, 184))

    # Clue Box
    round_rect(d, [rx + 40, 285, rx + 790, 600], 20, fill=(24, 30, 46, 220), outline=(245, 158, 11, 100), width=1)
    d.text((rx + 65, 310), "SORU 4 / 10  ·  CANLI YARISMA", font=font("bold", 15), fill=(245, 158, 11))
    d.text((rx + 690, 310), "7s", font=font("bold", 20), fill=(239, 68, 68))

    round_rect(d, [rx + 65, 345, rx + 765, 355], 5, fill=(45, 55, 80))
    round_rect(d, [rx + 65, 345, rx + 560, 355], 5, fill=(16, 185, 129))

    d.text((rx + 65, 380), "Bu efsanevi replik hangi basyapita aittir?", font=font("bold", 23), fill=(255, 255, 255))
    quote = "“You talkin' to me? You talkin' to me? Then who the hell else\nare you talkin' to? Well, I'm the only one here.”"
    d.text((rx + 65, 430), quote, font=font("medium", 19), fill=(251, 191, 36), spacing=6)
    d.text((rx + 65, 530), "Ipucu: 1976 Altin Palmiye odullu Martin Scorsese klasigi.", font=font("medium", 16), fill=(148, 163, 184))

    # Leaderboard
    d.text((rx + 40, 630), "Canli Liderlik Tablosu:", font=font("bold", 20), fill=(255, 255, 255))
    players = [
        ("Alptug", "80 Puan", "Hizli Yanit (+20)", (16, 185, 129)),
        ("Gamze", "60 Puan", "Dogru (+10)", (56, 189, 248)),
        ("Misafir (Salondaki Dost)", "40 Puan", "Dogru (+10)", (245, 158, 11))
    ]
    for p_idx, (p_name, score, bonus, col) in enumerate(players):
        py = 675 + p_idx * 56
        round_rect(d, [rx + 40, py, rx + 790, py + 48], 12, fill=(26, 33, 50), outline=(45, 58, 85), width=1)
        d.text((rx + 60, py + 12), p_name, font=font("bold", 17), fill=(255, 255, 255))
        d.text((rx + 420, py + 14), bonus, font=font("medium", 14), fill=col)
        d.text((rx + 680, py + 12), score, font=font("bold", 17), fill=(255, 255, 255))

    round_rect(d, [rx + 40, 860, rx + 790, 930], 16, fill=(245, 158, 11, 40), outline=(245, 158, 11), width=1)
    d.text((rx + 90, 882), "10 Soru Sonunda: “Bugun, kazanan hangi filmi secerse o izlenir!”", font=font("bold", 18), fill=(251, 191, 36))

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

    for y in range(h):
        r = int(12 + (y / h) * 10)
        g = int(14 + (y / h) * 12)
        b = int(20 + (y / h) * 18)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    d.text((120, 80), "CineSub · Sahneyi Durdurmayan Interaktif Kulturel Altyazi Rehberi", font=font("bold", 22), fill=(148, 163, 184))
    d.text((120, 126), "2001: A Space Odyssey — 'Also Sprach Zarathustra' Sahnesi", font=font("black", 38), fill=(255, 255, 255))

    # Widescreen cinematic frame
    round_rect(d, [120, 200, 1260, 890], 24, fill=(14, 18, 28), outline=(60, 75, 105), width=2)
    d.text((450, 500), "[ 4K SINEMA YAYINI KESINTISIZ AKIYOR ]", font=font("bold", 24), fill=(70, 85, 115))
    d.text((360, 550), "Film durmaz, ses kesilmez; kulturel not karti sag kenarda suzulur.", font=font("medium", 18), fill=(100, 116, 139))

    # Subtitle bar at bottom of film frame
    round_rect(d, [260, 790, 1120, 850], 12, fill=(0, 0, 0, 180))
    d.text((310, 808), "“Insan asilmasi gereken bir koprudur; bir gaye degil, bir gecistir.”", font=font("medium", 19), fill=(254, 240, 138))

    # Floating CineSub Cultural Note Card (right side)
    card_x = 1310
    round_rect(d, [card_x, 200, card_x + 490, 890], 24, fill=(20, 25, 38, 245), outline=(99, 102, 241, 200), width=2)

    round_rect(d, [card_x + 28, 230, card_x + 280, 268], 10, fill=(99, 102, 241, 40), outline=(99, 102, 241), width=1)
    d.text((card_x + 40, 240), "CINESUB KULTUREL REHBER", font=font("bold", 13), fill=(165, 180, 252))

    d.text((card_x + 28, 290), "Nietzsche ve 'Ustinsan'\nFelsefi Gondermesi", font=font("black", 24), fill=(255, 255, 255), spacing=4)

    card_text = (
        "Bu sahnede calan Richard Strauss bestesi\n"
        "'Also Sprach Zarathustra', Friedrich Nietzsche'nin\n"
        "ayni adli felsefi eserine dogrudan saygi durusudur.\n\n"
        "Kubrick, ilkel maymundan uzay cagi insanina\n"
        "ve oradan da Yildiz Cocuga (Star Child) gecisi,\n"
        "Nietzsche'nin 'Ustinsan' (Ubermensch)\n"
        "kavraminin gorsel bir alegorisi olarak kurgulamistir."
    )
    d.text((card_x + 28, 380), card_text, font=font("medium", 16), fill=(226, 232, 240), spacing=6)

    # Gemini Vision Inspector Box
    round_rect(d, [card_x + 24, 670, card_x + 466, 850], 16, fill=(15, 20, 32), outline=(56, 189, 248, 120), width=1)
    d.text((card_x + 40, 690), "GEMINI VISION SAHNE NESNESI", font=font("bold", 13), fill=(56, 189, 248))
    d.text((card_x + 40, 720), "Nesne: Siyah Monolit (TMA-1)", font=font("bold", 17), fill=(255, 255, 255))
    vision_txt = (
        "Geometrik oran: 1 : 4 : 9 (ilk uc asal sayi karesi).\n"
        "Uzayli bilincin ve zeka evriminin sicrama tetikleyicisi."
    )
    d.text((card_x + 40, 755), vision_txt, font=font("medium", 14), fill=(148, 163, 184), spacing=4)

    # Bottom footer notice
    round_rect(d, [120, 940, 1800, 1000], 14, fill=(16, 20, 30), outline=(45, 55, 75), width=1)
    d.text((150, 955), "Kumandadaki Sari tusa veya mobil kumandadaki CineSub butonuna basildiginda aninda belirir · Sifir arabellek duraklamasi", font=font("medium", 16), fill=(148, 163, 184))

    out = os.path.join(DOCS_MEDIA, "cinesub_overlay.png")
    im.save(out, "PNG")
    print("Saved:", out)

if __name__ == "__main__":
    generate_nuvio_home()
    generate_roulette_view()
    generate_cineparty_games()
    generate_cinesub_overlay()
