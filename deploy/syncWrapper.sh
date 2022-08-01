#!/usr/bin/env bash
source /etc/profile
adb root
if [ ! $? -eq 0 ] ;then
    echo 'please root your android devices'
    exit 2
fi

file_exist=`adb shell ls /data/data/com.virjar.hermes.hermesagent/ |  grep hermesModules`
if [ ! -n file_exist ] ;then
    adb shell mkdir /data/data/com.virjar.hermes.hermesagent/hermesModules/
    adb shell chmod 777 /data/data/com.virjar.hermes.hermesagent/hermesModules/
fi
adb push $1 $2
adb shell chmod 777 $2