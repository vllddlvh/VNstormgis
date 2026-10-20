/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        // San Rita Alpine Field Guide Design System Tokens
        mint: "#e2ffcc", // Highlighter Mint (Primary brand accent)
        sage: "#84907f", // Sage Stone (Muted secondary accent)
        carbon: "#161b13", // Carbon Ink (Dark section background / canvas)
        charcoal: "#2d3329", // Forest Charcoal (Borders, mid-dark text)
        topo: "#dde2e4", // Topo Gray (Base canvas light / card surfaces)
        black: "#000000",

        // Bảng màu phân cấp giao diện WebGIS theo San Rita Tokens
        gis: {
          bg: "#161b13", // Carbon Ink nền chính
          panel: "#1a2117", // Bảng điều khiển tối
          border: "#2d3329", // Viền Forest Charcoal
          hover: "#242c20", // Trạng thái hover
          text: "#dde2e4", // Chữ Topo Gray
          muted: "#84907f", // Chữ chú thích Sage Stone
          accent: "#e2ffcc", // Điểm nhấn Highlighter Mint
        },

        // Bảng màu phân cấp cảnh báo thiên tai chuẩn nghiệp vụ
        weather: {
          safe: "#e2ffcc", // Cấp 1: Highlighter Mint - An toàn
          advisory: "#84907f", // Cấp 2: Sage Stone - Chú ý / Mưa rào
          warning: "#f59e0b", // Cấp 3: Vàng hổ phách - Cảnh báo / Mưa to
          severe: "#ea580c", // Cấp 4: Cam đậm - Nguy hiểm / Mưa rất to
          extreme: "#dc2626", // Cấp 5: Đỏ cờ - Khẩn cấp / Bão mạnh
          catastrophic: "#7c3aed", // Cấp thảm họa: Tím thẫm
        },
      },
      fontFamily: {
        display: ["'Bebas Neue'", "'Oswald'", "ui-sans-serif", "sans-serif"],
        mono: [
          "'JetBrains Mono'",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "Monaco",
          "Consolas",
          "monospace",
        ],
        times: ["'Times New Roman'", "Times", "serif"],
      },
      borderRadius: {
        none: "0px",
        sm: "0px",
        DEFAULT: "0px",
        md: "0px",
        lg: "0px",
        xl: "0px",
        "2xl": "0px",
        "3xl": "0px",
        full: "0px",
      },
      boxShadow: {
        none: "none",
        flat: "2px 2px 0px rgba(0, 0, 0, 0.5)",
        "flat-lg": "4px 4px 0px rgba(0, 0, 0, 0.5)",
      },
      letterSpacing: {
        tightest: "-0.01em",
      },
      lineHeight: {
        display: "0.90",
        chrome: "1.20",
      },
    },
  },
  plugins: [],
};
