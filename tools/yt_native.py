#!/usr/bin/env python3
import argparse
import json
import os
import sys

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
YT_DLP_DIR = os.path.join(ROOT_DIR, "yt-dlp-master")
if os.path.isdir(YT_DLP_DIR):
    sys.path.insert(0, YT_DLP_DIR)

from yt_dlp import YoutubeDL


def build_format(quality: str, fmt: str) -> str:
    if fmt == "mp3":
        return "bestaudio/best"
    if quality == "best":
        return "bestvideo+bestaudio/best"
    if quality == "hd_1080":
        return "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
    if quality == "hd_720":
        return "bestvideo[height<=720]+bestaudio/best[height<=720]"
    if quality == "sd_480":
        return "bestvideo[height<=480]+bestaudio/best[height<=480]"
    if quality == "sd_360":
        return "bestvideo[height<=360]+bestaudio/best[height<=360]"
    return "bestvideo+bestaudio/best"


def cmd_info(args) -> int:
    ydl_opts = {
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "nocheckcertificate": True,
        "skip_download": True,
    }
    with YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(args.url, download=False)
        payload = {
            "id": info.get("id"),
            "title": info.get("title"),
            "uploader": info.get("uploader"),
            "duration": info.get("duration") or 0,
            "thumbnail": info.get("thumbnail"),
        }
        print(json.dumps(payload))
    return 0


def cmd_download(args) -> int:
    outtmpl = os.path.join(args.output, "%(title).200s_%(id)s.%(ext)s")
    fmt = build_format(args.quality, args.format)

    def progress_hook(d):
        if d.get("status") == "downloading":
            downloaded = d.get("downloaded_bytes") or 0
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            print(f"PROGRESS {downloaded} {total}", flush=True)
        elif d.get("status") == "finished":
            print("FINISHED", flush=True)

    ydl_opts = {
        "format": fmt,
        "outtmpl": outtmpl,
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "nocheckcertificate": True,
        "progress_hooks": [progress_hook],
        "merge_output_format": "mp4" if args.format == "mp4" else "webm",
    }

    if args.format == "mp3":
        ydl_opts["postprocessors"] = [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }
        ]

    with YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(args.url, download=True)
        filepath = info.get("_filename") or ydl.prepare_filename(info)
        print(f"FILEPATH {filepath}", flush=True)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_info = sub.add_parser("info")
    p_info.add_argument("--url", required=True)

    p_dl = sub.add_parser("download")
    p_dl.add_argument("--url", required=True)
    p_dl.add_argument("--output", required=True)
    p_dl.add_argument("--quality", required=True)
    p_dl.add_argument("--format", required=True)

    args = parser.parse_args()
    if args.cmd == "info":
        return cmd_info(args)
    if args.cmd == "download":
        return cmd_download(args)
    return 1


if __name__ == "__main__":
    sys.exit(main())
