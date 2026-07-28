#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/bake-playlist-hard-subtitles.sh <deepseek-api-key> <youtube-playlist-url> <start-index> <end-index> [output.mp4]
EOF
}

die() {
  echo "Error: $*" >&2
  echo >&2
  usage >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    die "missing required command: $1"
  fi
}

ensure_parent_dir() {
  local path=$1
  local parent
  parent=$(dirname "$path")
  if [[ "$parent" != "." ]]; then
    mkdir -p "$parent"
  fi
}

filter_escape_path() {
  local value=$1
  value=${value//\\/\\\\}
  value=${value//\'/\\\'}
  value=${value//:/\\:}
  value=${value//,/\\,}
  value=${value//[/\\[}
  value=${value//]/\\]}
  printf '%s' "$value"
}

concat_escape_path() {
  local value=$1
  value=${value//\'/\'\\\'\'}
  printf '%s' "$value"
}

retry_delay() {
  case "$1" in
    1) printf '2' ;;
    2) printf '5' ;;
    *) printf '10' ;;
  esac
}

retry_run() {
  local stage=$1
  local item=$2
  shift 2
  local attempt=1
  local total_attempts=$((retry_count + 1))
  local exit_code
  local delay

  while true; do
    if "$@"; then
      return 0
    else
      exit_code=$?
    fi
    if (( attempt >= total_attempts )); then
      echo "Error: index $item stage '$stage' failed after $attempt attempt(s)" >&2
      return "$exit_code"
    fi
    delay=$(retry_delay "$attempt")
    echo "Warning: index $item stage '$stage' failed on attempt $attempt; retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
}

state_value() {
  local state_file=$1
  local key=$2
  if [[ ! -f "$state_file" ]]; then
    return 0
  fi
  awk -v key="$key" '
    {
      separator = index($0, "=")
      if (separator > 0 && substr($0, 1, separator - 1) == key) {
        value = substr($0, separator + 1)
      }
    }
    END { if (value != "") print value }
  ' "$state_file"
}

set_state_value() {
  local state_file=$1
  local key=$2
  local value=$3
  local temporary_state="${state_file}.tmp.$$"
  if [[ -f "$state_file" ]]; then
    awk -F= -v key="$key" '$1 != key' "$state_file" > "$temporary_state"
  else
    : > "$temporary_state"
  fi
  printf '%s=%s\n' "$key" "$value" >> "$temporary_state"
  mv "$temporary_state" "$state_file"
}

remove_source_subtitles() {
  local entry_dir=$1
  find "$entry_dir" -maxdepth 1 -type f \( -name 'source*.vtt' -o -name 'source*.srt' \) -delete
}

valid_subtitle() {
  [[ -s "$1" ]]
}

has_media_stream() {
  local selector=$1
  local media=$2
  [[ -n "$(ffprobe -v error -select_streams "$selector" -show_entries stream=codec_type -of csv=p=0 "$media")" ]]
}

media_duration() {
  ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$1"
}

valid_positive_duration() {
  local duration=$1
  awk -v duration="$duration" 'BEGIN { exit !(duration + 0 > 0) }'
}

valid_media() {
  local media=$1
  local duration
  [[ -s "$media" ]] || return 1
  has_media_stream v:0 "$media" || return 1
  has_media_stream a:0 "$media" || return 1
  duration=$(media_duration "$media") || return 1
  valid_positive_duration "$duration"
}

duration_matches_expected() {
  local media=$1
  local actual
  actual=$(media_duration "$media") || return 1
  awk -v expected="$expected_duration" -v actual="$actual" '
    BEGIN {
      difference = expected - actual
      if (difference < 0) difference = -difference
      tolerance = expected * 0.01
      if (tolerance < 2.0) tolerance = 2.0
      exit !(expected > 0 && actual > 0 && difference <= tolerance)
    }
  '
}

initialize_job_manifest() {
  local manifest=$1
  if [[ -f "$manifest" ]]; then
    if [[ "$(state_value "$manifest" playlist_url)" != "$playlist_url" \
        || "$(state_value "$manifest" start_index)" != "$start_index" \
        || "$(state_value "$manifest" end_index)" != "$end_index" ]]; then
      die "PLAYLIST_WORK_DIR belongs to a different playlist job: $work_dir"
    fi
    return
  fi

  if [[ -n "$(find "$work_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    die "PLAYLIST_WORK_DIR is not empty and has no Video Oven job manifest: $work_dir"
  fi

  local temporary_manifest="${manifest}.tmp.$$"
  printf 'playlist_url=%s\n' "$playlist_url" > "$temporary_manifest"
  printf 'start_index=%s\n' "$start_index" >> "$temporary_manifest"
  printf 'end_index=%s\n' "$end_index" >> "$temporary_manifest"
  printf 'source_language=en\n' >> "$temporary_manifest"
  printf 'target_language=zh-CN\n' >> "$temporary_manifest"
  mv "$temporary_manifest" "$manifest"
}

download_video_once() {
  local index=$1
  local destination=$2
  local format
  for format in \
      "bv*+ba/bestvideo*+bestaudio/best" \
      "best[ext=mp4]/best" \
      ""; do
    if [[ -n "$format" ]]; then
      if yt-dlp ${yt_dlp_options[@]+"${yt_dlp_options[@]}"} \
          --playlist-items "$index" \
          --ffmpeg-location "$ffmpeg_cmd" \
          -f "$format" \
          --merge-output-format mp4 \
          -o "$destination" \
          "$playlist_url" && valid_media "$destination"; then
        return 0
      fi
    elif yt-dlp ${yt_dlp_options[@]+"${yt_dlp_options[@]}"} \
        --playlist-items "$index" \
        --ffmpeg-location "$ffmpeg_cmd" \
        --merge-output-format mp4 \
        -o "$destination" \
        "$playlist_url" && valid_media "$destination"; then
      return 0
    fi
    echo "Warning: index $index video format was unavailable or invalid; trying fallback" >&2
  done
  return 1
}

download_subtitles_once() {
  local index=$1
  local output_template=$2
  yt-dlp ${yt_dlp_options[@]+"${yt_dlp_options[@]}"} \
    --playlist-items "$index" \
    --skip-download \
    --write-subs \
    --write-auto-subs \
    --sub-langs "en,en-.*" \
    --sub-format "vtt/srt" \
    -o "$output_template" \
    "$playlist_url"
}

translate_subtitles_once() {
  local translation_input=$1
  local translated_subtitle=$2
  java -jar "$jar" \
    --input "$translation_input" \
    --output "$translated_subtitle" \
    --translator deepseek \
    --deepseek-api-key "$deepseek_api_key" \
    --mode bilingual \
    --source en \
    --target zh-CN \
    && valid_subtitle "$translated_subtitle"
}

burn_subtitles_once() {
  local source_video=$1
  local translated_subtitle=$2
  local baked_video=$3
  local subtitle_filter_path
  subtitle_filter_path=$(filter_escape_path "$translated_subtitle")
  "$ffmpeg_cmd" \
    -y \
    -hide_banner \
    -i "$source_video" \
    -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,subtitles='$subtitle_filter_path':force_style='FontName=PingFang SC'" \
    -c:v libx264 \
    -crf 18 \
    -preset medium \
    -pix_fmt yuv420p \
    -c:a aac \
    -ar 48000 \
    -ac 2 \
    -movflags +faststart \
    "$baked_video" \
    && valid_media "$baked_video"
}

combine_videos_once() {
  local concat_file=$1
  local destination=$2
  "$ffmpeg_cmd" \
    -y \
    -hide_banner \
    -f concat \
    -safe 0 \
    -i "$concat_file" \
    -c copy \
    "$destination" \
    && valid_media "$destination" \
    && duration_matches_expected "$destination"
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)

if [[ ${1:-} == "-h" || ${1:-} == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 4 || $# -gt 5 ]]; then
  die "expected four or five arguments"
fi

deepseek_api_key=$1
playlist_url=$2
start_index=$3
end_index=$4
output=${5:-playlist.baked.zh-en.mp4}
retry_count=${PLAYLIST_RETRY_COUNT:-3}

if [[ -z "$deepseek_api_key" ]]; then
  die "DeepSeek API key is required"
fi
if [[ -z "$playlist_url" ]]; then
  die "YouTube playlist URL is required"
fi
if [[ ! "$start_index" =~ ^[1-9][0-9]*$ ]]; then
  die "start index must be a positive integer"
fi
if [[ ! "$end_index" =~ ^[1-9][0-9]*$ ]]; then
  die "end index must be a positive integer"
fi
if (( start_index > end_index )); then
  die "start index must not be greater than end index"
fi
if [[ ! "$retry_count" =~ ^[0-9]+$ ]]; then
  die "PLAYLIST_RETRY_COUNT must be a non-negative integer"
fi
if [[ -n "${YT_DLP_COOKIES_FROM_BROWSER:-}" && -n "${YT_DLP_COOKIES:-}" ]]; then
  die "use only one of YT_DLP_COOKIES_FROM_BROWSER or YT_DLP_COOKIES"
fi

yt_dlp_options=()
if [[ -n "${YT_DLP_COOKIES_FROM_BROWSER:-}" ]]; then
  yt_dlp_options+=(--cookies-from-browser "$YT_DLP_COOKIES_FROM_BROWSER")
elif [[ -n "${YT_DLP_COOKIES:-}" ]]; then
  yt_dlp_options+=(--cookies "$YT_DLP_COOKIES")
fi

jar="$project_dir/target/video-oven-0.1.0.jar"
if [[ ! -f "$jar" ]]; then
  die "Prebuilt jar is required: $jar. Build it first with: mvn clean package"
fi

if [[ -x "$project_dir/tool/ffmpeg" ]]; then
  ffmpeg_cmd="$project_dir/tool/ffmpeg"
else
  require_command ffmpeg
  ffmpeg_cmd=ffmpeg
fi
require_command java
require_command yt-dlp
require_command ffprobe

work_dir=${PLAYLIST_WORK_DIR:-.playlist-work}
mkdir -p "$work_dir"
initialize_job_manifest "$work_dir/job.properties"
ensure_parent_dir "$output"
concat_file="$work_dir/concat.txt"
: > "$concat_file"
expected_duration=0

for (( index=start_index; index<=end_index; index++ )); do
  index_name=$(printf 'index-%03d' "$index")
  entry_dir="$work_dir/$index_name"
  source_video="$entry_dir/source.mp4"
  translated_subtitle="$entry_dir/translated.zh-en.srt"
  baked_video="$entry_dir/baked.mp4"
  state_file="$entry_dir/state.properties"
  mkdir -p "$entry_dir"

  source_changed=false
  if [[ "$(state_value "$state_file" download)" == "done" ]] && valid_media "$source_video"; then
    echo "[$index/$end_index] Reusing downloaded video"
  else
    echo "[$index/$end_index] Downloading video"
    set_state_value "$state_file" download pending
    set_state_value "$state_file" subtitle pending
    set_state_value "$state_file" translation pending
    set_state_value "$state_file" baked pending
    rm -f "$source_video" "$translated_subtitle" "$baked_video"
    remove_source_subtitles "$entry_dir"
    retry_run "video download" "$index" download_video_once "$index" "$source_video"
    set_state_value "$state_file" download done
    source_changed=true
  fi

  subtitle_changed=false
  subtitle_mode=$(state_value "$state_file" subtitle)
  source_subtitle=$(find "$entry_dir" -maxdepth 1 -type f \( -name 'source*.vtt' -o -name 'source*.srt' \) | sort | head -n 1)
  if [[ "$source_changed" == false && "$subtitle_mode" == "youtube" && -n "$source_subtitle" ]] && valid_subtitle "$source_subtitle"; then
    translation_input=$source_subtitle
    echo "[$index/$end_index] Reusing downloaded subtitles"
  elif [[ "$source_changed" == false && "$subtitle_mode" == "whisper" ]]; then
    translation_input=$source_video
    echo "[$index/$end_index] Reusing Whisper subtitle source"
  else
    echo "[$index/$end_index] Looking for YouTube subtitles"
    set_state_value "$state_file" subtitle pending
    set_state_value "$state_file" translation pending
    set_state_value "$state_file" baked pending
    remove_source_subtitles "$entry_dir"
    rm -f "$translated_subtitle" "$baked_video"
    retry_run "subtitle download" "$index" download_subtitles_once "$index" "$entry_dir/source.%(ext)s"
    source_subtitle=$(find "$entry_dir" -maxdepth 1 -type f \( -name 'source*.vtt' -o -name 'source*.srt' \) | sort | head -n 1)
    if [[ -n "$source_subtitle" ]] && valid_subtitle "$source_subtitle"; then
      translation_input=$source_subtitle
      set_state_value "$state_file" subtitle youtube
    else
      translation_input=$source_video
      set_state_value "$state_file" subtitle whisper
      echo "[$index/$end_index] No YouTube subtitles found; using Whisper"
    fi
    subtitle_changed=true
  fi

  translation_changed=false
  if [[ "$source_changed" == false && "$subtitle_changed" == false && "$(state_value "$state_file" translation)" == "done" ]] \
      && valid_subtitle "$translated_subtitle"; then
    echo "[$index/$end_index] Reusing translated subtitles"
  else
    echo "[$index/$end_index] Translating subtitles"
    set_state_value "$state_file" translation pending
    set_state_value "$state_file" baked pending
    rm -f "$translated_subtitle" "$baked_video"
    retry_run "subtitle translation" "$index" translate_subtitles_once "$translation_input" "$translated_subtitle"
    set_state_value "$state_file" translation done
    translation_changed=true
  fi

  if [[ "$source_changed" == false && "$translation_changed" == false && "$(state_value "$state_file" baked)" == "done" ]] \
      && valid_media "$baked_video"; then
    echo "[$index/$end_index] Reusing baked video"
  else
    echo "[$index/$end_index] Burning bilingual subtitles"
    set_state_value "$state_file" baked pending
    rm -f "$baked_video"
    retry_run "subtitle burning" "$index" burn_subtitles_once "$source_video" "$translated_subtitle" "$baked_video"
    set_state_value "$state_file" baked done
  fi

  absolute_baked_video="$(cd "$(dirname "$baked_video")" && pwd)/$(basename "$baked_video")"
  printf "file '%s'\n" "$(concat_escape_path "$absolute_baked_video")" >> "$concat_file"
  segment_duration=$(media_duration "$baked_video")
  expected_duration=$(awk -v total="$expected_duration" -v segment="$segment_duration" 'BEGIN { printf "%.6f", total + segment }')
done

temporary_output="${output}.partial.$$.mp4"
echo "Combining playlist entries -> $output"
if ! retry_run "final concatenation" "all" combine_videos_once "$concat_file" "$temporary_output"; then
  echo "Error: combined video failed validation; work files were preserved in $work_dir" >&2
  exit 1
fi
mv "$temporary_output" "$output"

echo "Done: $output"
