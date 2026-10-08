#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "== Stube: lấy SharedModules + MediaServiceCore =="

if [ -e SharedModules/.git ] || [ "$(find SharedModules -mindepth 1 -maxdepth 1 2>/dev/null | head -1)" ]; then
  echo "SharedModules không rỗng. Dừng để tránh ghi đè."
  exit 1
fi
if [ -e MediaServiceCore/.git ] || [ "$(find MediaServiceCore -mindepth 1 -maxdepth 1 2>/dev/null | head -1)" ]; then
  echo "MediaServiceCore không rỗng. Dừng để tránh ghi đè."
  exit 1
fi

rm -rf SharedModules MediaServiceCore
git submodule add https://github.com/aleixrodriala/SharedModules.git SharedModules
git submodule add https://github.com/aleixrodriala/MediaServiceCore.git MediaServiceCore

echo
echo "Đã lấy đủ 2 module. Kiểm tra:"
ls SharedModules/core_settings.gradle MediaServiceCore/core_settings.gradle

echo
echo "Xong. Bây giờ build:"
echo "./gradlew clean :smarttubetv:assembleStmobileRelease"
