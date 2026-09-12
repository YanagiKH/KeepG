# Synthetic media fixture

`editor-sample.mp4` contains two seconds of a solid teal 160x120 H.264 image at 12 fps and a silent mono AAC audio track. It contains no personal media. The audio track is intentional: the export test verifies that mute removes it.

Reproduce using FFmpeg (encoded bytes may vary by FFmpeg/x264 version):

```sh
ffmpeg -y -f lavfi -i 'color=c=0x328f78:s=160x120:r=12:d=2' \
  -f lavfi -i 'anullsrc=r=8000:cl=mono:d=2' \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset ultrafast \
  -bsf:v filter_units=remove_types=6 -c:a aac -b:a 8k \
  -shortest -movflags +faststart editor-sample.mp4
```
