function restartAdb()
{
         echo "$1 restart adbd"
         restart_url="http://$1:5597/restartAdbD"
         echo ${restart_url}
         curl --connect-timeout 1 ${restart_url}
         if [ ! $? -eq 0 ] ;then
            echo 'hermesServer is down ,devices offline'
            return 4
         fi
         echo
         echo "disconnect devices"
         adb disconnect "$1:4555"
         echo
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

function reload_hermes()
{
    line=$1
    echo "adb -s $line:4555 shell am start -n \"com.virjar.hermes.hermesagent/com.virjar.hermes.hermesagent.MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
    adb -s ${line}:4555 shell am start -n "com.virjar.hermes.hermesagent/com.virjar.hermes.hermesagent.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    #adb -s $line:4555 shell am broadcast -a android.intent.action.PACKAGE_REPLACED -n de.robv.android.xposed.installer/de.robv.android.xposed.installer.receivers.PackageChangeReceiver
    echo 'sleep 5s ,wait for hermes http server startup'
    sleep 5s

    echo "kill hermes agent, to reload"

    adb -s ${line}:4555  shell am kill "com.virjar.hermes.hermesagent"
    adb -s ${line}:4555  shell am force-stop "com.virjar.hermes.hermesagent"
    sleep 5s

    echo "restart hermes agent again..."
    echo "adb -s $line:4555 shell am start -n \"com.virjar.hermes.hermesagent/com.virjar.hermes.hermesagent.MainActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
    adb -s ${line}:4555 shell am start -n "com.virjar.hermes.hermesagent/com.virjar.hermes.hermesagent.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    #adb -s $line:4555 shell am broadcast -a android.intent.action.PACKAGE_REPLACED -n de.robv.android.xposed.installer/de.robv.android.xposed.installer.receivers.PackageChangeReceiver
    echo 'sleep 5s ,wait for hermes http server startup'
    sleep 5s

    #echo "curl --connect-timeout 10 \"http://$line:5597/reloadService\""
    #curl --connect-timeout 10 "http://$line:5597/reloadService"
    echo "reboot devices"
    adb -s ${line}:4555 shell reboot
}

function check_version()
{
    target_version=$1
    if [[ -z ${target_version} ]] ;then
        return 0
    fi
    getVersionUrl="http://${line}:5597/agentVersion"
    echo "curl ${getVersionUrl}"
    version=`curl --connect-timeout 1  ${getVersionUrl}`
    if [ ! $? -eq 0 ] ;then
        return 2
    fi
    echo "agent version:${version}"
    echo "${target_version}"
    if [ "${version}"x = "${target_version}"x ] ;then
        echo "the agent upgrade already"
        return 0
    fi
    return 1
}

function parse_apk_version()
{
    apk_location=$1
    if [ $2 ] ;then
        ANDROID_HOME_LOCAL=$2
    else
        ANDROID_HOME_LOCAL=${ANDROID_HOME}
    fi
    if [ ${ANDROID_HOME_LOCAL} ] ;then
        last_version_sdk=`ls ${ANDROID_HOME_LOCAL}/build-tools | tail -n 1`
        aapt_location=${ANDROID_HOME_LOCAL}/build-tools/${last_version_sdk}/aapt
        if [  -f ${aapt_location} ] ;then
            parsed_version=`${aapt_location} dump badging ${apk_location}   | grep versionCode | grep package | awk '{split($0,a," "); for(i in a) {print a[i];}}' | grep "versionCode" | awk -F "\\'" '{print $2}'`
            return ${parsed_version}
        fi
    fi
    gradle_local_properties_file=`pwd`/../local.properties
    if [ -f ${gradle_local_properties_file} ] ;then
        ANDROID_HOME_LOCAL=`cat ${gradle_local_properties_file} | grep "sdk.dir=" | head -1 | awk -F "sdk.dir=" '{print $2}'`
        parse_apk_version ${apk_location} ${ANDROID_HOME_LOCAL}
    fi
}

function buildApk()
{
    apk_location=`pwd`/../app/build/outputs/apk/release/app-release.apk
    if  [ -f ${apk_location} ] ;then
        parse_apk_version ${apk_location}
        now_apk_version=$?
        if [ "${now_apk_version}"x == "$1"x ] ;then
            echo "hermes agent apk build already,skip build apk"
            return
        fi
    fi
    now_dir=`pwd`
    cd ../
    echo "build hermes agent apk"
    ./gradlew app:clean
    ./gradlew app:assembleRelease
    if [ ! $? -eq 0 ] ;then
        echo 'apk assemble failed'
        exit -1
    fi
    cd ${now_dir}
}

function deployOneDevice()
{
    line=$1
    target_version=$2
    apk_location=$3
    if [[ ${line} == "#"* ]] ;then
        return 0
    fi
    check_version ${target_version}
    check_version_result=$?
    if [[ ${check_version_result} == 0  ]] ;then
        return 0
    fi

    echo 'connect device' ${line}
    connect ${line}

    echo "test adb status"
    adb_status=`adb devices | grep ${line}`
    echo ${adb_status}

    if [[ ${adb_status} =~ 'offline' ]] ;then
           echo "device offline"
           return 1
       fi

    if [[ -z "$adb_status" ]] ;then
           echo "device offline"
           return 1
    fi

    check_version ${target_version}
    check_version_result=$?
    if [[ ${check_version_result} == 2  ]] ;then
        return 1
        continue
    fi
    if [[ ${check_version_result} == 0  ]] ;then
        return 0
    fi

    #adb -s $line:4555 shell am start -n "de.robv.android.xposed.installer/de.robv.android.xposed.installer.WelcomeActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

    echo "adb -s $line:4555 push ${apk_location} /data/local/tmp/com.virjar.hermes.hermesagent"
    adb -s ${line}:4555 push ${apk_location} /data/local/tmp/com.virjar.hermes.hermesagent

    echo "adb -s $line:4555 shell pm install -t -r \"/data/local/tmp/com.virjar.hermes.hermesagent\""
    adb -s ${line}:4555 shell pm install -t -r "/data/local/tmp/com.virjar.hermes.hermesagent"

    # 目前reload机制存在问题
    # reload_hermes ${line}
    echo "adb -s ${line}:4555 shell reboot"
    adb -s ${line}:4555 shell reboot

    echo
    echo "$line deploy success"
    return 0
}

function main_flow()
{
    cd `dirname $0`
    offline_list=('')

    build_config_file=`pwd`/../app/build.gradle
    codeVersion=`cat ${build_config_file} | grep versionCode | head -1 | awk '{print $2}'`
    echo "deploy for apk version: ${codeVersion}"

    buildApk ${codeVersion}
    device_list_file="devices_list.txt"
    apk_location=`pwd`/../app/build/outputs/apk/release/app-release.apk
    device_list=`curl "https://www.virjar.com/hermes/device/deviceIpList"`

    if [[ -z ${device_list} ]] ;then
        device_list=`cat ${device_list_file}`
        echo 'access online device list failed,use local config'
    fi

    echo 'deploy device list '${device_list}

    for line in ${device_list}
    do
        deployOneDevice ${line} ${codeVersion} ${apk_location}
        deploy_status=$?
        if [[ ${deploy_status} -eq 0 ]]; then
            continue
        fi
        offline_list[${#offline_list[@]}]=${line}
    done

    echo
    echo "deploy task execute end"
    if [ ${#offline_list[@]} -eq 0 ] ;then
        echo 'install failed  device list:'
    fi

    for offline_device in ${offline_list[@]}; do
        echo ${offline_device};
    done
}

main_flow
# echo ${ANDROID_HOME}

