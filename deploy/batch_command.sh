#!/usr/bin/env bash

#
# you can execute some command for all devices such as:
# ./deploy/batch_command.sh 'rm -f /data/data/com.smile.gifmaker/cache/hermes_*'
# or execute command group by a command file such as:
# ./deploy/batch_command.sh path_to_shell.sh
#

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



function executeOnAllDevices(){
    command=$*

    device_list=`curl "https://www.virjar.com/hermes/device/deviceIpList"`

    for line in ${device_list}
    do
        echo "execute command \"${command}\" for device:${line}"
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
        echo
        if [ -f "${command}" ] ; then
            for command_item in `cat ${command}`
            do
                adb -s ${line}:4555 shell "${command_item}"
            done
        else
            echo adb -s ${line}:4555 shell "${command}"
            adb -s ${line}:4555 shell "${command}"
        fi
    done
}



executeOnAllDevices $1