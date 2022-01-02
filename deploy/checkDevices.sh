cd `dirname $0`

offline_list=('')

for line in `cat devices_list.txt` ;do
    if [[ $line == "#"* ]] ;then
        continue
    fi
    ping_url="http://$line:5597/ping"
    echo $ping_url
    ping_result=`curl --connect-timeout 2 $ping_url`
    if [ ! $? -eq 0 ] ;then
        echo "$line offline"
        offline_list[${#offline_list[@]}]=$line
    fi
done

if [ ${#offline_list[@]} != 0 ] ;then
    echo 'offline device list:'
fi

for offline_device in ${offline_list[@]}; do
    echo $offline_device;
done