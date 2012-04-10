#!/bin/sh

set -e

DIR="`dirname "${BASH_SOURCE[0]}"`"
root="${DIR}/../"

mkIcon()
{
  bucket=$1
  sz=$2
  out="$root/res/drawable-${bucket}/ic_launcher.png"

  inkscape -z -D -e "$out" -w $sz -h $sz "${DIR}/icon.svg"
}

mkIcon ldpi 36
mkIcon mdpi 48
mkIcon hdpi 72
mkIcon xhdpi 96

