/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class', // dark-mode-first toggle support
  theme: {
    extend: {
      colors: {
        brand: {
          blue: '#4ebecf',  // z1n-blue (Cyan-Blue)
          pink: '#b950d2',  // z1n-pink (Magenta-Pink)
          grey: '#dfdfdf',  // z1n-grey (Neutral Light)
        },
        slate: {
          950: '#020617',   // Dark Sidebar Mode
        }
      },
      backgroundImage: {
        'z1n-blue-pink': 'linear-gradient(to right, #4ebecf, #b950d2)',
        'z1n-blue-black': 'linear-gradient(to right, #4ebecf, #000000)',
        'z1n-pink-black': 'linear-gradient(to right, #b950d2, #000000)',
      },
      fontFamily: {
        sans: ['Inter', 'Outfit', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
