#!/usr/bin/env bash
### Install all dependencies on Ubuntu 16.04 LTS (Xenial Xerus) for RChain dev environment


## Detect if running in docker container - setup using sudo accordingly
if [[ $(cat /proc/self/cgroup  | grep docker) = *docker* ]]; then
    echo "Running in docker container!"
    sudo=""
else
	sudo="sudo"
fi

## Set BASH environment so it will fail properly throwing exit code
set -euxo pipefail

## Verify operating system (OS) version is Ubuntu 16.04 LTS (Xenial Xerus)
# Add more versions as necessary
version=$(cat /etc/*release | grep "^VERSION_ID" | awk -F= '{print $2}' | sed 's/"//g')
if [[ "$version" == "16.04" ]]; then
    echo "Running install on Ubuntu 16.04" 
else
    echo "Error: Not running on Ubuntu 16.04"
    echo "Exiting"
    exit
fi

## Resynchronize the package index files from their sources
${sudo} apt-get update -yqq

## Install g++ multilib for cross-compiling as rosette is currently only 32-bit 
${sudo} apt-get install g++-multilib -yqq

# sleep 1
${sudo} apt-get install cmake -yqq
${sudo} apt-get install git -yqq
${sudo} apt-get install wget -yqq
${sudo} apt-get install curl -yqq

## Install Haskell Platform
# ref: https://www.haskell.org/platform/#linux-ubuntu
# ref: https://www.haskell.org/platform/ # all platforms
${sudo} apt-get install haskell-platform -yqq

## Install BNFC Converter 
# ref: http://bnfc.digitalgrammars.com/
git clone https://github.com/BNFC/bnfc.git
cd bnfc/source
${sudo} cabal install --global

## Install Java OpenJDK 8
# sudo apt-get install openjdk-8*
${sudo} apt-get update -yqq
${sudo} apt-get install default-jdk -yqq

## Install Scala 2.12.4
scala_version="2.12.4"
${sudo} apt-get remove scala-library scala -yqq
curl --location --output /tmp/scala-${scala_version}.deb http://scala-lang.org/files/archive/scala-${scala_version}.deb 
${sudo} dpkg -i /tmp/scala-${scala_version}.deb
${sudo} apt-get update -yqq
${sudo} apt-get install scala -yqq

## Install sbt 
${sudo} apt-get install apt-transport-https
echo "deb https://dl.bintray.com/sbt/debian /" | ${sudo} tee -a /etc/apt/sources.list.d/sbt.list
${sudo} apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
${sudo} apt-get update -yqq
${sudo} apt-get install sbt -yqq

## Install jflex
${sudo} apt-get install jflex -yqq

