import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        realis: {
          50:  "#f0f4ff",
          100: "#e0e9ff",
          200: "#c0d3ff",
          300: "#89acff",
          400: "#5478e0",
          500: "#2d52c4",
          600: "#2344a8",
          700: "#1a348a",
          800: "#132470",
          900: "#0d1c52",
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
