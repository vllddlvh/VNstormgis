/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        // Bảng màu phân cấp cảnh báo thời tiết thiên tai chuẩn nghiệp vụ
        weather: {
          safe: "#10B981", // Cấp 1: Xanh lục - An toàn / Bình thường
          advisory: "#0EA5E9", // Cấp 2: Xanh lam - Chú ý / Mưa rào
          warning: "#F59E0B", // Cấp 3: Vàng hổ phách - Cảnh báo / Mưa to
          severe: "#F97316", // Cấp 4: Cam đậm - Nguy hiểm / Bão
          extreme: "#EF4444", // Cấp 5: Đỏ cờ - Khẩn cấp / Siêu bão
          catastrophic: "#A855F7", // Cấp thảm họa: Tím
        },
        // Tông màu giao diện tối chuyên dụng cho bản đồ WebGIS
        gis: {
          bg: "#0F172A", // Nền bảng điều khiển (Slate 900)
          panel: "#1E293B", // Nền hộp thoại nổi (Slate 800)
          border: "#334155", // Viền phân cách (Slate 700)
          hover: "#475569", // Màu khi hover chuột (Slate 600)
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "-apple-system", "sans-serif"],
      },
    },
  },
  plugins: [],
};
