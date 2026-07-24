import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// WSL에서 /mnt/c 같은 Windows 드라이브 마운트(DrvFs)는 파일 변경 시 inotify 이벤트를
// 안정적으로 전달하지 못해 기본 파일 감시로는 HMR이 동작하지 않는 경우가 있다. 폴링으로 대체.
export default defineConfig({
  plugins: [vue()],
  server: {
    watch: {
      usePolling: true,
    },
  },
})
