set -e
echo Making jar
./gradlew jar
echo Copying
cp ./build/libs/* ../mserv/config/mods/

