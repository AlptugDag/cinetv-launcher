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
# 2. SINEROULETTE: 3D GUNMETAL REVOLVER CYLINDER (docs/media/roulette_wheel.png)
# -------------------------------------------------------------
def draw_plain_poster(dest_img, poster_path, bbox, corner_radius, sheen=True):
    pw = int(bbox[2] - bbox[0])
    ph = int(bbox[3] - bbox[1])
    if os.path.isfile(poster_path):
        p_img = Image.open(poster_path).convert("RGBA")
        p_resized = p_img.resize((pw, ph), Image.Resampling.BICUBIC)
        mask = Image.new("L", (pw, ph), 0)
        dmask = ImageDraw.Draw(mask)
        dmask.rounded_rectangle([0, 0, pw, ph], radius=corner_radius, fill=255)
        dest_img.paste(p_resized, (int(bbox[0]), int(bbox[1])), mask)
        
        if sheen:
            sheen_layer = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
            dsheen = ImageDraw.Draw(sheen_layer)
            dsheen.polygon([(0, 0), (pw, 0), (0, int(ph * 0.52))], fill=(255, 255, 255, 50))
            dest_img.paste(sheen_layer, (int(bbox[0]), int(bbox[1])), mask)
    else:
        d = ImageDraw.Draw(dest_img)
        round_rect(d, bbox, corner_radius, fill=(30, 41, 59), outline=(56, 189, 248), width=1)

def generate_roulette_view():
    w, h = 1920, 1080
    im = Image.new("RGBA", (w, h), (9, 11, 17, 255))
    d = ImageDraw.Draw(im)

    # Ambient deep theater background matching CineTV
    for y in range(h):
        r = int(10 + (y / h) * 14)
        g = int(12 + (y / h) * 10)
        b = int(20 + (y / h) * 22)
        d.line([(0, y), (w, y)], fill=(r, g, b, 255))

    # Header with SineRoulette wordmark ("Sine" in white, "Roulette" in electric blue)
    d.text((120, 56), "✦ SİNEFİL FİLM SEÇİCİ · REVOLVER GÖBEĞİ SİNEMA TAMBURU (TV & MOBİL)", font=font("bold", 18), fill=(56, 189, 248))
    
    # Wordmark
    sine_txt = "Sine"
    d.text((120, 88), sine_txt, font=font("black", 48), fill=(255, 255, 255))
    sine_w = font("black", 48).getbbox(sine_txt)[2]
    d.text((120 + sine_w, 88), "Roulette", font=font("black", 48), fill=(56, 189, 248))
    d.text((120, 155), "Herkes kendi telefonundan 'Oyunlar' sekmesine girip bir film fırlatır. 3D revolver tamburu döner ve kazanan film taçlandırılır!", font=font("medium", 19), fill=(148, 163, 184))

    # Left: Photorealistic 3D Gunmetal Revolver Cylinder (Diameter ~600px)
    cx, cy = 520, 600
    R = 275

    # Gunmetal Cylinder Base
    for r_i in range(R, 0, -2):
        frac = r_i / R
        cr = int(15 + frac * 28)
        cg = int(22 + frac * 32)
        cb = int(35 + frac * 48)
        d.ellipse([cx - r_i, cy - r_i, cx + r_i, cy + r_i], fill=(cr, cg, cb, 255))

    # Outer Steel Chamfer Bevel Ring
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(100, 116, 139), width=3)
    d.ellipse([cx - R + 4, cy - R + 4, cx + R - 4, cy + R - 4], outline=(51, 65, 85), width=2)

    # 6 Bullet Chambers (Mermi Yuvaları) with real TMDB movie posters
    chambers = [
        ("Taxi Driver", "taxi_driver.jpg"),
        ("Interstellar", "interstellar.jpg"),
        ("The Godfather", "godfather.jpg"),
        ("Pulp Fiction", "pulp_fiction.jpg"),
        ("Fight Club", "fight_club.jpg"),
        ("Inception", "inception.jpg")
    ]
    n_ch = len(chambers)
    ch_dist = R * 0.62
    ch_r = R * 0.26

    # Revolver Outer Flutes (Scalloped cutouts between chambers)
    for i in range(n_ch):
        mid_ang = math.radians(i * (360 / n_ch) + (180 / n_ch) - 90)
        fx = cx + R * math.cos(mid_ang)
        fy = cy + R * math.sin(mid_ang)
        flute_r = R * 0.22
        d.ellipse([fx - flute_r, fy - flute_r, fx + flute_r, fy + flute_r], fill=(6, 8, 14, 255), outline=(51, 65, 85), width=1)

    # Chambers and Movie Posters
    posters_dir = os.path.join(DOCS_MEDIA, "posters")
    for i, (m_title, p_file) in enumerate(chambers):
        ang = math.radians(i * (360 / n_ch) - 90) # Taxi Driver at top 12 o'clock (-90 deg)
        ch_x = cx + ch_dist * math.cos(ang)
        ch_y = cy + ch_dist * math.sin(ang)

        # Bore hole deep shadow
        d.ellipse([ch_x - ch_r - 2, ch_y - ch_r - 2, ch_x + ch_r + 2, ch_y + ch_r + 2], fill=(6, 9, 14), outline=(217, 119, 6), width=2)
        d.ellipse([ch_x - ch_r, ch_y - ch_r, ch_x + ch_r, ch_y + ch_r], fill=(10, 14, 22))

        # Real Movie Poster (DVD-boxless, 2:3 ratio, rounded corners)
        pw = int(ch_r * 1.25)
        ph = int(pw * 1.48)
        p_bbox = [ch_x - pw // 2, ch_y - ph // 2, ch_x + pw // 2, ch_y + ph // 2]
        p_path = os.path.join(posters_dir, p_file)
        draw_plain_poster(im, p_path, p_bbox, corner_radius=int(pw * 0.085))

    # Center Extractor Star Ratchet Gear (Yıldız Dişli)
    rat_r = R * 0.22
    d.ellipse([cx - rat_r, cy - rat_r, cx + rat_r, cy + rat_r], fill=(15, 23, 42), outline=(56, 189, 248), width=2)
    # Center pin
    d.ellipse([cx - rat_r * 0.45, cy - rat_r * 0.45, cx + rat_r * 0.45, cy + rat_r * 0.45], fill=(148, 163, 184), outline=(248, 250, 252), width=1)

    # Top Hammer Sight Alignment Notch (pointing at 12 o'clock winning chamber)
    top_y = cy - R - 6
    notch_w, notch_h = 24, 34
    d.polygon([(cx - notch_w // 2, top_y - notch_h * 0.6), (cx + notch_w // 2, top_y - notch_h * 0.6), (cx, top_y + notch_h * 0.5)],
              fill=(56, 189, 248), outline=(255, 255, 255))
    d.ellipse([cx - 4, top_y - notch_h * 0.35, cx + 4, top_y - notch_h * 0.35 + 8], fill=(255, 255, 255))

    # Right: Modal Card & Winner Card
    rx = 980
    round_rect(d, [rx, 240, rx + 820, 890], 28, fill=(20, 24, 36, 230), outline=(56, 189, 248, 140), width=2)

    # Winner Card (Taxi Driver crowned)
    round_rect(d, [rx + 36, 280, rx + 784, 460], 20, fill=(56, 189, 248, 28), outline=(56, 189, 248, 220), width=2)
    d.text((rx + 60, 305), "BU AKŞAMIN SEÇİLEN BAŞYAPITI (PREMİYER SEÇİMİ)", font=font("bold", 15), fill=(56, 189, 248))
    d.text((rx + 60, 335), "Taxi Driver (1976)", font=font("black", 38), fill=(255, 255, 255))
    d.text((rx + 60, 395), "Öneren: Alptuğ  ·  Martin Scorsese  ·  Robert De Niro", font=font("medium", 18), fill=(203, 213, 225))

    # Suggestions pool list
    d.text((rx + 36, 495), "Revolver Tamburundaki Öneriler:", font=font("bold", 20), fill=(255, 255, 255))
    suggested = [
        ("Taxi Driver", "Alptuğ", (56, 189, 248)),
        ("Interstellar", "Gamze", (16, 185, 129)),
        ("Pulp Fiction", "Misafir 1", (239, 68, 68)),
        ("The Godfather", "Misafir 2", (245, 158, 11))
    ]
    for s_idx, (m_title, user, col) in enumerate(suggested):
        sy = 540 + s_idx * 58
        round_rect(d, [rx + 36, sy, rx + 784, sy + 48], 14, fill=(30, 38, 56, 220), outline=(50, 65, 95), width=1)
        d.text((rx + 56, sy + 12), "• " + m_title, font=font("bold", 17), fill=(255, 255, 255))
        d.text((rx + 640, sy + 14), "(" + user + ")", font=font("medium", 15), fill=col)

    # Controls row
    round_rect(d, [rx + 36, 800, rx + 520, 860], 16, fill=(14, 165, 233), outline=None)
    d.text((rx + 160, 818), "🎯 Revolver Tamburunu Çevir", font=font("bold", 20), fill=(255, 255, 255))

    round_rect(d, [rx + 540, 800, rx + 784, 860], 16, fill=(35, 42, 60, 200), outline=(70, 80, 110), width=1)
    d.text((rx + 610, 818), "Temizle", font=font("medium", 20), fill=(203, 213, 225))

    out = os.path.join(DOCS_MEDIA, "roulette_wheel.png")
    im.save(out, "PNG")
    print("Saved:", out)

# -------------------------------------------------------------
# 3. CINEPARTY: SNAKEPHILE & SINEQUIZ (docs/media/cineparty_games.png)
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
    d.text((100, 48), "✦ CİNEPARTY: OTURMA ODASI İNTERAKTİF SİNEMA OYUNLARI", font=font("bold", 18), fill=(16, 185, 129))
    d.text((100, 84), "SnakePhile 3D Yılanı ve 10 Soruluk SineQuiz Arenası", font=font("black", 42), fill=(255, 255, 255))

    posters_dir = os.path.join(DOCS_MEDIA, "posters")

    # Left: SnakePhile Game Card (Fullscreen Cinema Deck)
    lx = 100
    round_rect(d, [lx, 165, lx + 830, 995], 28, fill=(16, 20, 30, 235), outline=(16, 185, 129, 160), width=2)
    
    # Wordmark for SnakePhile
    snk_txt = "Snake"
    d.text((lx + 40, 198), snk_txt, font=font("black", 26), fill=(255, 255, 255))
    snk_w = font("black", 26).getbbox(snk_txt)[2]
    d.text((lx + 40 + snk_w, 198), "Phile", font=font("black", 26), fill=(16, 185, 129))
    d.text((lx + 40 + snk_w + 80, 204), "· 3D GERÇEKÇİ SİNEFİL YILANI (GYRO EĞİMLİ)", font=font("bold", 14), fill=(16, 185, 129))
    d.text((lx + 40, 236), "Telefonunuzu sağa-sola, öne-arkaya eğerek yönetin; gerçek film afişlerini yedikçe uzayın!", font=font("medium", 15), fill=(148, 163, 184))

    # HUD Bar
    round_rect(d, [lx + 40, 275, lx + 790, 325], 12, fill=(24, 30, 44), outline=(45, 58, 85), width=1)
    d.text((lx + 60, 288), "KARE (SKOR): 180", font=font("bold", 16), fill=(16, 185, 129))
    d.text((lx + 360, 288), "REKOR: 420", font=font("bold", 16), fill=(245, 158, 11))
    d.text((lx + 580, 288), "HEDEF AFİŞ: Interstellar", font=font("medium", 15), fill=(56, 189, 248))

    # 3D Cinema Board (lx + 175, 350, 480x480)
    bx, by, b_size = lx + 175, 350, 480
    round_rect(d, [bx, by, bx + b_size, by + b_size], 22, fill=(9, 12, 18), outline=(16, 185, 129, 90), width=2)

    # Ambient subtle projection grid lines
    grid_n = 12
    cell = b_size / grid_n
    for c in range(1, grid_n):
        d.line([(bx + c * cell, by), (bx + c * cell, by + b_size)], fill=(16, 185, 129, 15))
        d.line([(bx, by + c * cell), (bx + b_size, by + c * cell)], fill=(16, 185, 129, 15))

    # Food: REAL TMDB MOVIE POSTER (Interstellar, DVD-boxless with glow)
    fx = bx + 8 * cell + 2
    fy = by + 3 * cell
    food_w = int(cell * 1.3)
    food_h = int(food_w * 1.48) # 2:3 aspect ratio
    
    # Ambient cyan bloom glow behind poster
    round_rect(d, [fx - 8, fy - 8, fx + food_w + 8, fy + food_h + 8], 16, fill=(2, 132, 199, 45))
    round_rect(d, [fx - 4, fy - 4, fx + food_w + 4, fy + food_h + 4], 12, fill=(2, 132, 199, 80))
    # Plain poster drawing (no DVD case)
    interstellar_path = os.path.join(posters_dir, "interstellar.jpg")
    draw_plain_poster(im, interstellar_path, [fx, fy, fx + food_w, fy + food_h], corner_radius=int(food_w * 0.085))

    # Snake: REALISTIC 3D ORGANIC SNAKE (Scales, Head, Reptilian Eyes, Flicking Tongue)
    snake_coords = [(6, 5), (5, 5), (4, 5), (3, 5), (3, 6), (3, 7), (4, 7), (5, 7), (6, 7)]
    for s_idx, (sx, sy) in enumerate(snake_coords):
        scx = bx + sx * cell + cell // 2
        scy = by + sy * cell + cell // 2
        seg_r = (cell // 2) - 1.5
        t = s_idx / len(snake_coords)
        curr_r = seg_r * max(0.48, 1 - t * 0.5)

        # Soft organic shadow under body
        d.ellipse([scx - curr_r + 2, scy - curr_r + 4, scx + curr_r + 2, scy + curr_r + 4], fill=(0, 0, 0, 150))

        if s_idx == 0:
            # HEAD: 3D Viper Head
            d.ellipse([scx - curr_r, scy - curr_r, scx + curr_r, scy + curr_r], fill=(22, 163, 74), outline=(74, 222, 128), width=2)
            d.ellipse([scx - curr_r * 0.6, scy - curr_r * 0.6, scx + curr_r * 0.6, scy + curr_r * 0.6], fill=(34, 197, 94))
            
            # Eyes: Golden Reptilian Eyes with vertical slit pupils
            # Facing right towards food (food is at x=8, y=3)
            eye_offset_x = curr_r * 0.4
            eye_offset_y = curr_r * 0.45
            for e_y in [scy - eye_offset_y, scy + eye_offset_y]:
                d.ellipse([scx + eye_offset_x - 4, e_y - 4, scx + eye_offset_x + 4, e_y + 4], fill=(251, 191, 36))
                d.rectangle([scx + eye_offset_x - 1, e_y - 3, scx + eye_offset_x + 1, e_y + 3], fill=(2, 44, 34))

            # Flicking Red Tongue (pointing forward toward food)
            tongue_x = scx + curr_r + 2
            d.line([(scx + curr_r, scy), (tongue_x + 10, scy)], fill=(239, 68, 68), width=2)
            d.line([(tongue_x + 10, scy), (tongue_x + 15, scy - 4)], fill=(239, 68, 68), width=2)
            d.line([(tongue_x + 10, scy), (tongue_x + 15, scy + 4)], fill=(239, 68, 68), width=2)

        else:
            # BODY SEGMENT: 3D Cylindrical Scales
            d.ellipse([scx - curr_r, scy - curr_r, scx + curr_r, scy + curr_r], fill=(21, 128, 61), outline=(134, 239, 172, 160), width=1)
            d.ellipse([scx - curr_r * 0.6, scy - curr_r * 0.6, scx + curr_r * 0.6, scy + curr_r * 0.6], fill=(34, 197, 94))

    d.text((lx + 235, 850), "Eğim Sensörü Aktif  ·  Dokunmatik D-Pad Destekli", font=font("medium", 15), fill=(148, 163, 184))
    round_rect(d, [lx + 180, 885, lx + 650, 950], 16, fill=(16, 185, 129), outline=None)
    d.text((lx + 335, 905), "Oyunu Başlat", font=font("bold", 20), fill=(10, 20, 15))

    # Right: SineQuiz Arena Card
    rx = 990
    round_rect(d, [rx, 165, rx + 830, 995], 28, fill=(16, 20, 30, 235), outline=(139, 92, 246, 160), width=2)
    
    # Wordmark for SineQuiz
    sq_txt = "Sine"
    d.text((rx + 40, 198), sq_txt, font=font("black", 26), fill=(255, 255, 255))
    sq_w = font("black", 26).getbbox(sq_txt)[2]
    d.text((rx + 40 + sq_w, 198), "Quiz", font=font("black", 26), fill=(139, 92, 246))
    d.text((rx + 40 + sq_w + 70, 204), "· 10 SORULUK CANLI YARIŞMA ARENASI", font=font("bold", 14), fill=(139, 92, 246))
    d.text((rx + 40, 236), "Ekrana aktör portresi gelir, 10s içinde bilin; kazanan akşamın filmini seçer!", font=font("medium", 15), fill=(148, 163, 184))

    # Question Card with Actor Portrait and Live Buzzer
    round_rect(d, [rx + 40, 275, rx + 790, 620], 20, fill=(22, 28, 44, 230), outline=(139, 92, 246, 120), width=1)
    
    # Top bar: Soru 1 / 10 and Countdown Ring
    d.text((rx + 65, 298), "SORU 1 / 10  ·  AKTÖR TANIMA", font=font("bold", 15), fill=(139, 92, 246))
    round_rect(d, [rx + 680, 292, rx + 765, 328], 10, fill=(239, 68, 68, 40), outline=(239, 68, 68), width=1)
    d.text((rx + 705, 300), "10s", font=font("bold", 17), fill=(248, 113, 113))

    # Clue Text
    d.text((rx + 65, 342), "Bu ünlü aktör kimdir?", font=font("bold", 22), fill=(255, 255, 255))
    d.text((rx + 65, 372), "İpucu: Oppenheimer, Peaky Blinders, Inception", font=font("medium", 15), fill=(148, 163, 184))

    # 3D Actor Portrait Frame with REAL Cillian Murphy Photograph
    ax, ay, aw, ah = rx + 65, 410, 160, 180
    round_rect(d, [ax - 2, ay - 2, ax + aw + 2, ay + ah + 2], 12, fill=(10, 14, 22), outline=(139, 92, 246), width=2)
    cillian_path = os.path.join(posters_dir, "cillian_murphy.jpg")
    if os.path.isfile(cillian_path):
        c_img = Image.open(cillian_path).convert("RGBA")
        c_resized = c_img.resize((aw, ah), Image.Resampling.BICUBIC)
        c_mask = Image.new("L", (aw, ah), 0)
        ImageDraw.Draw(c_mask).rounded_rectangle([0, 0, aw, ah], radius=10, fill=255)
        im.paste(c_resized, (ax, ay), c_mask)
        # Specular sheen
        sheen_layer = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
        ImageDraw.Draw(sheen_layer).polygon([(0, 0), (aw, 0), (0, int(ah * 0.45))], fill=(255, 255, 255, 45))
        im.paste(sheen_layer, (ax, ay), c_mask)
        d = ImageDraw.Draw(im)

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
    round_rect(d, [rx + 40, 875, rx + 790, 945], 16, fill=(139, 92, 246, 35), outline=(139, 92, 246), width=1)
    d.text((rx + 75, 897), "10 Soru Sonunda: “Bugün, kazanan hangi filmi seçerse o izlenir!”", font=font("bold", 17), fill=(196, 181, 253))

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
