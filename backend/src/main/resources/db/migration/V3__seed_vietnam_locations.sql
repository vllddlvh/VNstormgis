-- ============================================================================
-- Database Migration Script: V3__seed_vietnam_locations.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 1 (Week 1 - Day 2) | Task: W1-06
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Author: GIS / Database Specialist & Technical Lead
-- Description:
--   1. Nạp dữ liệu trắc địa chuẩn WGS84 (EPSG:4326) cho toàn bộ 63 tỉnh/thành phố
--      trực thuộc Trung ương và các trạm trắc địa hải đảo tiền tiêu chiến lược
--      trên lãnh thổ và vùng biển Việt Nam (tổng cộng 68 điểm quan trắc).
--   2. Tuân thủ nghiêm ngặt quy tắc thứ tự tọa độ PostGIS:
--      ST_SetSRID(ST_MakePoint(kinh_độ_long, vĩ_độ_lat), 4326)
--      với Longitude: 102.1°E - 114.5°E, Latitude: 8.5°N - 23.4°N.
--   3. Đảm bảo tính lũy đẳng (Strict Idempotency) thông qua mệnh đề
--      ON CONFLICT (code) DO UPDATE, cho phép tái thực thi an toàn trong CI/CD.
-- ============================================================================

INSERT INTO locations (code, name, region, geom, is_active) VALUES
-- ============================================================================
-- 1. CÁC ĐÔ THỊ TRỰC THUỘC TRUNG ƯƠNG & CỰC TRỌNG ĐIỂM KINH TẾ (5 ĐIỂM)
-- ============================================================================
('HAN', 'Hà Nội', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.8542, 21.0285), 4326), true),
('HPH', 'Hải Phòng', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.6881, 20.8449), 4326), true),
('DNG', 'Đà Nẵng', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(108.2022, 16.0544), 4326), true),
('SGN', 'TP. Hồ Chí Minh', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.6297, 10.8231), 4326), true),
('VCA', 'Cần Thơ', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.7469, 10.0452), 4326), true),

-- ============================================================================
-- 2. ĐỒNG BẰNG & DUYÊN HẢI BẮC BỘ (9 TỈNH - VEN BIỂN HỨNG CHỊU BÃO ĐỔ BỘ)
-- ============================================================================
('QNH', 'Quảng Ninh', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(107.0433, 20.9505), 4326), true),
('THB', 'Thái Bình', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.3366, 20.4463), 4326), true),
('NDH', 'Nam Định', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.1683, 20.4375), 4326), true),
('NBH', 'Ninh Bình', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.9745, 20.2506), 4326), true),
('HDG', 'Hải Dương', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.3161, 20.9372), 4326), true),
('HYN', 'Hưng Yên', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.0511, 20.6461), 4326), true),
('BNH', 'Bắc Ninh', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.0754, 21.1861), 4326), true),
('VPC', 'Vĩnh Phúc', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.5969, 21.3090), 4326), true),
('HNM', 'Hà Nam', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.9229, 20.5453), 4326), true),

-- ============================================================================
-- 3. TRUNG DU & MIỀN NÚI PHÍA BẮC (14 TỈNH - NGUY CƠ MƯA LỚN, LŨ QUÉT, SẠT LỞ)
-- ============================================================================
('LCI', 'Lào Cai', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(103.9757, 22.4856), 4326), true),
('YBI', 'Yên Bái', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(104.8954, 21.7168), 4326), true),
('HGG', 'Hà Giang', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(104.9784, 22.8233), 4326), true),
('CBG', 'Cao Bằng', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.2574, 22.6664), 4326), true),
('LSN', 'Lạng Sơn', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.7615, 21.8537), 4326), true),
('TNN', 'Thái Nguyên', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.8442, 21.5942), 4326), true),
('BKG', 'Bắc Kạn', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.8348, 22.1470), 4326), true),
('TQG', 'Tuyên Quang', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.2144, 21.8234), 4326), true),
('PTO', 'Phú Thọ', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.2149, 21.3228), 4326), true),
('BGG', 'Bắc Giang', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(106.1946, 21.2731), 4326), true),
('DBN', 'Điện Biên', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(103.0168, 21.3860), 4326), true),
('LCU', 'Lai Châu', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(103.4684, 22.3964), 4326), true),
('SLA', 'Sơn La', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(103.9148, 21.3283), 4326), true),
('HBH', 'Hòa Bình', 'Bắc Bộ', ST_SetSRID(ST_MakePoint(105.3376, 20.8164), 4326), true),

-- ============================================================================
-- 4. BẮC TRUNG BỘ (6 TỈNH - TÂM ĐIỂM ĐÓN BÃO NHIỆT ĐỚI ĐỔ BỘ HÀNG NĂM)
-- ============================================================================
('THA', 'Thanh Hóa', 'Bắc Trung Bộ', ST_SetSRID(ST_MakePoint(105.7850, 19.8077), 4326), true),
('NAN', 'Nghệ An', 'Bắc Trung Bộ', ST_SetSRID(ST_MakePoint(105.6813, 18.6734), 4326), true),
('HTH', 'Hà Tĩnh', 'Bắc Trung Bộ', ST_SetSRID(ST_MakePoint(105.9057, 18.3435), 4326), true),
('QBH', 'Quảng Bình', 'Bắc Trung Bộ', ST_SetSRID(ST_MakePoint(106.6225, 17.4764), 4326), true),
('QTI', 'Quảng Trị', 'Bắc Trung Bộ', ST_SetSRID(ST_MakePoint(107.1854, 16.8163), 4326), true),
('HUE', 'Thừa Thiên Huế', 'Bắc Trung Bộ', ST_SetSRID(ST_MakePoint(107.5905, 16.4637), 4326), true),

-- ============================================================================
-- 5. DUYÊN HẢI NAM TRUNG BỘ (7 TỈNH - SÓNG LỚN, TRIỀU CƯỜNG, NGUY CƠ BÃO LỚN)
-- ============================================================================
('QNM', 'Quảng Nam', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(108.3380, 15.5736), 4326), true),
('QNG', 'Quảng Ngãi', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(108.7961, 15.1205), 4326), true),
('BDH', 'Bình Định', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(109.2197, 13.7830), 4326), true),
('PYN', 'Phú Yên', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(109.3150, 13.0882), 4326), true),
('KHA', 'Khánh Hòa', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(109.1967, 12.2388), 4326), true),
('NTH', 'Ninh Thuận', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(108.9882, 11.5658), 4326), true),
('BTH', 'Bình Thuận', 'Nam Trung Bộ', ST_SetSRID(ST_MakePoint(108.1021, 10.9333), 4326), true),

-- ============================================================================
-- 6. TÂY NGUYÊN (5 TỈNH - VÙNG CAO NGUYÊN, LŨ QUÉT VÀ SẠT LỞ ĐẤT)
-- ============================================================================
('KTM', 'Kon Tum', 'Tây Nguyên', ST_SetSRID(ST_MakePoint(108.0076, 14.3497), 4326), true),
('GLI', 'Gia Lai', 'Tây Nguyên', ST_SetSRID(ST_MakePoint(108.0005, 13.9833), 4326), true),
('DLK', 'Đắk Lắk', 'Tây Nguyên', ST_SetSRID(ST_MakePoint(108.0383, 12.6667), 4326), true),
('DKN', 'Đắk Nông', 'Tây Nguyên', ST_SetSRID(ST_MakePoint(107.6876, 12.0039), 4326), true),
('LDG', 'Lâm Đồng', 'Tây Nguyên', ST_SetSRID(ST_MakePoint(108.4583, 11.9404), 4326), true),

-- ============================================================================
-- 7. ĐÔNG NAM BỘ (5 TỈNH - ĐÔ THỊ HÓA CAO, TRIỀU CƯỜNG VÀ NGẬP LỤT ĐÔ THỊ)
-- ============================================================================
('BDG', 'Bình Dương', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.6661, 11.0064), 4326), true),
('DNI', 'Đồng Nai', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.8427, 10.9460), 4326), true),
('VTU', 'Bà Rịa - Vũng Tàu', 'Nam Bộ', ST_SetSRID(ST_MakePoint(107.0843, 10.3460), 4326), true),
('TNH', 'Tây Ninh', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.1098, 11.3104), 4326), true),
('BPC', 'Bình Phước', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.8766, 11.7511), 4326), true),

-- ============================================================================
-- 8. ĐỒNG BẰNG SÔNG CỬU LONG / TÂY NAM BỘ (12 TỈNH - TRIỀU CƯỜNG, XÂM NHẬP MẶN)
-- ============================================================================
('LAN', 'Long An', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.4134, 10.5362), 4326), true),
('TGG', 'Tiền Giang', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.3639, 10.3544), 4326), true),
('BTE', 'Bến Tre', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.3759, 10.2433), 4326), true),
('TVH', 'Trà Vinh', 'Nam Bộ', ST_SetSRID(ST_MakePoint(106.3458, 9.9347), 4326), true),
('VLG', 'Vĩnh Long', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.9722, 10.2537), 4326), true),
('DTP', 'Đồng Tháp', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.6337, 10.4578), 4326), true),
('AGG', 'An Giang', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.1259, 10.3891), 4326), true),
('KGG', 'Kiên Giang', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.0809, 10.0125), 4326), true),
('HUG', 'Hậu Giang', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.4707, 9.7844), 4326), true),
('STG', 'Sóc Trăng', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.9744, 9.6033), 4326), true),
('BLU', 'Bạc Liêu', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.7244, 9.2941), 4326), true),
('CMU', 'Cà Mau', 'Nam Bộ', ST_SetSRID(ST_MakePoint(105.1500, 9.1769), 4326), true),

-- ============================================================================
-- 9. ĐẢO & QUẦN ĐẢO TIỀN TIÊU TRỌNG YẾU (5 ĐIỂM - CẢNH BÁO BÃO XA BIỂN ĐÔNG)
-- ============================================================================
('BLV', 'Đảo Bạch Long Vĩ', 'Biển Đông', ST_SetSRID(ST_MakePoint(107.7289, 20.1333), 4326), true),
('HSA', 'Quần đảo Hoàng Sa', 'Biển Đông', ST_SetSRID(ST_MakePoint(111.6000, 16.5000), 4326), true),
('TSA', 'Quần đảo Trường Sa', 'Biển Đông', ST_SetSRID(ST_MakePoint(114.3667, 8.6500), 4326), true),
('CDO', 'Côn Đảo', 'Biển Đông', ST_SetSRID(ST_MakePoint(106.6080, 8.6833), 4326), true),
('PQU', 'Đảo Phú Quốc', 'Biển Tây Nam', ST_SetSRID(ST_MakePoint(103.9667, 10.2833), 4326), true)

-- ============================================================================
-- BẢO ĐẢM TÍNH LŨY ĐẲNG (IDEMPOTENCY ENFORCEMENT)
-- ============================================================================
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    region = EXCLUDED.region,
    geom = EXCLUDED.geom,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
