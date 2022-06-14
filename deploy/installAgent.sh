function restartAdb()
{
         echo "$1 restart adbd"
         stop_url="http://$1:5597/executeCommand?useRoot=true&cmd=stop%20adbd"
         echo ${stop_url}
         stop_result=`curl --connect-timeout 2 ${stop_url}`
         if [ ! $? -eq 0 ] ;then
            echo 'hermesServer is down ,devices offline'
            return 4
         fi
         curl "http://$1:5597/executeCommand?useRoot=true&cmd=start%20adbd"
}

function connect()
{
    connect_result=`adb connect $1:4555`
    echo ${connect_result}
    if [[ ${connect_result} =~ 'unable to connect' ]] ;then
            restartAdb $1
            if [ ! $? -eq 0 ] ;then
                 return 5
            fi
            echo 'reconnect to '$1
            adb connect $1:4555
            if [ ! $? -eq 0 ] ;then
                 return 2
            fi
    fi
    echo 'switch to root user'
    adb -s $1:4555  root
    if [ ! $? -eq 0 ] ;then
         echo 'switch root user failed'
         restartAdb $1
         echo 'reconnect to '$1
         adb connect $1:4555
         if [ ! $? -eq 0 ] ;then
              return 2
         fi
    fi
    adb connect $1:4555
    if [ ! $? -eq 0 ] ;then
        return 3
    fi
}

cd `dirname $0`
cd ..
echo "build hermes agent apk"
./gradlew app:clean
./gradlew app:assembleRelease
if [ ! $? -eq 0 ] ;then
    echo 'apk assemble failed'
    exit -1
fi

cd `dirname $0`
offline_list=('')

device_list_file="devices_list.txt"
apk_location=`pwd`/../app/build/outputs/apk/release/app-release.apk

device_list=`curl "http://10.8.126.249:5597/device/deviceIpList"`

if [[ -z ${device_list} ]] ;then
    device_list = `cat ${device_list_file}`
    echo 'access online device list failed,use local config'
fi

echo 'deploy device list '${device_list}


for line in ${device_list}
do
    if [[ ${line} == "#"* ]] ;then
        continue
    fi
    echo 'connect device' ${line}
    connect ${line}

    echo "test adb status"
    adb_status=`adb devices | grep ${line}`
    echo ${adb_status}

    if [[ ${adb_status} =~ 'offline' ]] ;then
           echo "device offline"
           offline_list[${#offline_list[@]}]=${line}
           continue
       fi

    if [[ -z "$adb_status" ]] ;then
           echo "device offline"
           offline_list[${#offline_list[@]}]=${line}
           continue
    fi

    #adb -s $line:4555 shell am start -n "de.robv.android.xposed.installer/de.robv.android.xposed.installer.WelcomeActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

    echo "adb -s $line:4555 push ${apk_location} /data/local/tmp/com.virjar.hermes.hermesagent"
    adb -s ${line}:4555 push ${apk_location} /data/local/tmp/com.virjar.hermes.hermesagent

    echo "adb -s $line:4555 shell pm install -t -r \"/data/local/tmp/com.virjar.hermes.hermesagent\""
    adb -s ${line}:4555 shell pm install -t -r "/data/local/tmp/com.virjar.hermes.hermesagent"

    echo "adb -s $line:4555 shell am start -n \"com.virjar.hermes.hermesagent/com.virjar.hermes.hermesagent.MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
    adb -s ${line}:4555 shell am start -n "com.virjar.hermes.hermesagent/com.virjar.hermes.hermesagent.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    #adb -s $line:4555 shell am broadcast -a android.intent.action.PACKAGE_REPLACED -n de.robv.android.xposed.installer/de.robv.android.xposed.installer.receivers.PackageChangeReceiver
    #echo 'sleep 10s ,wait for hermes http server startup'
    #sleep 10s
    #这里超时时间设置的长一些，因为hermes刚刚安装重启，可能需要一点时间http服务才会开启。hermesAgent的安装，需要重启所有slave

    #echo "curl --connect-timeout 10 \"http://$line:5597/reloadService\""
    #curl --connect-timeout 10 "http://$line:5597/reloadService"
    echo "reboot devices"
    adb -s ${line}:4555 shell reboot
    echo
    echo "$line deploy success"
done

if [ ${#offline_list[@]} -eq 0 ] ;then
    echo 'install failed  device list:'
fi

for offline_device in ${offline_list[@]}; do
    echo ${offline_device};
done