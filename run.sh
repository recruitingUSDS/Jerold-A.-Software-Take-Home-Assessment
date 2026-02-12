#!/usr/bin/env bash
ARTIFACT=Jerold-A.-Software-Take-Home-Assessment-1.0-SNAPSHOT-all.jar
VERSION=1.0-SNAPSHOT
WITHDEP=all
printf "[\033[32;7mSTAGE 1\033[0m] \033[33m-------------------------------<\033[0m \033[32;7mBUILDING\033[0m ... \033[33m>-------------------------------\033[0m\n"

gradle clean shadowJar; RC=$?
if [[ $RC -eq 0 ]]; then
    echo
    echo
    if [[ $RC -eq 0 ]]; then
        printf "[\033[32;7mSTAGE 2\033[0m] \033[33m--------------------------------<\033[0m \033[32;7mRUNNING\033[0m ... \033[33m>-------------------------------\033[0m\n"
	java  -jar build/libs/$ARTIFACT
    fi	
    echo
    if [[ $RC -eq 0 ]]; then
        printf "[\033[32;7mCOMPLETE\033[0m] \033[32;1mSUCCESS\033[0m\n"
    else
        printf "[\033[32;7mCOMPLETE\033[0m] \0033[31;1mFAILED\033[0m\n"
    fi
else
    printf "[\033[31;7mCOMPLETE\033[0m] \033[31;1mFAILED\033[0m\n"
fi
echo
