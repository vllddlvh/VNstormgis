-- Nghị quyết 202/2025/QH15: 34 đơn vị hành chính cấp tỉnh sau sắp xếp.
-- Nghị quyết 30/2026/QH16: thành phố Đồng Nai từ 30/04/2026.
-- Giữ tọa độ và UUID của các điểm quan trắc để bảo toàn chuỗi thời gian và khóa ngoại.
-- Mã bên phải trong chú thích là điểm đại diện của đơn vị sau sắp xếp.
UPDATE locations
SET is_active = false
WHERE is_active = true AND code IN (
    'HGG',         -- Tuyên Quang (TQG)
    'YBI',         -- Lào Cai (LCI)
    'BKG',         -- Thái Nguyên (TNN)
    'VPC', 'HBH',  -- Phú Thọ (PTO)
    'BGG',         -- Bắc Ninh (BNH)
    'THB',         -- Hưng Yên (HYN)
    'HDG',         -- Hải Phòng (HPH)
    'HNM', 'NDH',  -- Ninh Bình (NBH)
    'QBH',         -- Quảng Trị (QTI)
    'QNM',         -- Đà Nẵng (DNG)
    'KTM',         -- Quảng Ngãi (QNG)
    'BDH',         -- Gia Lai (GLI)
    'NTH',         -- Khánh Hòa (KHA)
    'PYN',         -- Đắk Lắk (DLK)
    'DKN', 'BTH',  -- Lâm Đồng (LDG)
    'BDG', 'VTU',  -- Thành phố Hồ Chí Minh (SGN)
    'BPC',         -- Thành phố Đồng Nai (DNI)
    'LAN',         -- Tây Ninh (TNH)
    'HUG', 'STG',  -- Cần Thơ (VCA)
    'BTE', 'TVH',  -- Vĩnh Long (VLG)
    'TGG',         -- Đồng Tháp (DTP)
    'KGG',         -- An Giang (AGG)
    'BLU'          -- Cà Mau (CMU)
);

UPDATE locations
SET name = 'Huế'
WHERE code = 'HUE' AND name <> 'Huế';

UPDATE locations
SET name = 'TP. Đồng Nai'
WHERE code = 'DNI' AND name <> 'TP. Đồng Nai';

COMMENT ON TABLE locations IS
    'Danh mục điểm quan trắc đại diện 34 đơn vị cấp tỉnh, các điểm hải đảo và các điểm lịch sử';
COMMENT ON TABLE weather_records IS
    'Chuỗi thời gian quan trắc khí tượng từ OpenWeatherMap, gồm dữ liệu lịch sử của các điểm ngừng theo dõi';
