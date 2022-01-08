function restartAdb()
{
         echo "$1 restart adbd"
         stop_url="http://$1:5597/executeCommand?useRoot=true&cmd=stop%20adbd"
         echo $stop_url
         stop_result=`curl --connect-timeout 2 $stop_url`
         if [ ! $? -eq 0 ] ;then
            echo 'hermesServer is down ,devices offline'
            return 4
         fi
         curl "http://$1:5597/executeCommand?useRoot=true&cmd=start%20adbd"
}

function connect()
{
    connect_result=`adb connect $1:4555`
    echo $connect_result
    if [[ $connect_result =~ 'unable to connect' ]] ;then
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
offline_list=('')

device_list_file="devices_list.txt"
if [ $2 ] ;then
    device_list_file="devices_list_local_test.txt";
fi

for line in `cat ${device_list_file}`
do
    if [[ $line == "#"* ]] ;then
        continue
    fi
    echo 'connect device' $line
    connect $line
    if [ ! $? -eq 0 ] ;then
        echo "device ${line} shutdown,skip it"
        offline_list[${#offline_list[@]}]=$line
        continue
    fi
    adb -s $line:4555 shell am start -n "de.robv.android.xposed.installer/de.robv.android.xposed.installer.WelcomeActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    adb -s $line:4555 push $1 /data/local/tmp/com.virjar.hermes.hermesagent
    adb -s $line:4555 shell pm install -t -r "/data/local/tmp/com.virjar.hermes.hermesagent"
    #adb -s $line:4555 shell reboot
    echo 'sleep 5 s'
    sleep 5s
done

if [ ${#offline_list[@]} != 0 ] ;then
    echo 'install failed  device list:'
fi

for offline_device in ${offline_list[@]}; do
    echo $offline_device;
done