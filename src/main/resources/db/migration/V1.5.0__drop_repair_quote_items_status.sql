-- ==============================================================================
-- MotoFix DB Migration V1.5.0
-- Đợt BE05: Loại bỏ hoàn toàn status riêng của bảng repair_quote_items.
-- Quy tắc nghiệp vụ: Mỗi item lấy trạng thái hiệu lực tự động từ repair_quotes chứa nó.
-- Lưu ý: Không áp migration trực tiếp lên DB đang phục vụ người dùng (production)
--       mà tuân thủ quy trình release / bảo trì có kế hoạch.
-- ==============================================================================

-- 1. Nếu trên môi trường database cũ cột status đang có NOT NULL constraint, drop NOT NULL trước
--    để tránh việc các ứng dụng chưa cập nhật bị lỗi insert nếu chạy chuyển tiếp:
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'repair_quote_items'
          AND column_name = 'status'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE repair_quote_items ALTER COLUMN status DROP NOT NULL;
    END IF;
END $$;

-- 2. Xóa bỏ hoàn toàn cột status khỏi bảng repair_quote_items:
ALTER TABLE repair_quote_items DROP COLUMN IF EXISTS status;
