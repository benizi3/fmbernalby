#!/usr/bin/env bash
adb root
if [ ! $? -eq 0 ] ;then
    echo 'please root your android devices'
    return 2
fi
adb push $1 $2
file_exist=`adb shell ls /data/data/com.virjar.hermes.hermesagent/ |  grep hermesModules`
if [ ! -n file_exist ] ;then
    adb shell mkdir /data/data/com.virjar.hermes.hermesagent/hermesModules/
    adb shell chmod 666 /data/data/com.virjar.hermes.hermesagent/hermesModules/
fi

adb shell mv $2 /data/data/com.virjar.hermes.hermesagent/hermesModules/