# Usage

```bash
adb pull /sdcard/Download/h265.mp4 .
ffmpeg -i h265.mp4 -c:a copy -c:v libx265 hevc.mp4
adb push hevc.mp4 /sdcard/Download/
```
