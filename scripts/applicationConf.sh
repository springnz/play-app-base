#!/bin/bash

# FreeBSD (OSX) sed
sedcmd="sed -i ''"

# GNU (Linux) sed
if [[ $(sed --help 2>&1) = *GNU* ]]; then
  sedcmd="sed -i"
fi

TEMPLATE_CONF=src/test/resources/application.conf.template
TEST_APP_CONF=src/test/resources/application.conf

cp $TEMPLATE_CONF $TEST_APP_CONF

eval $sedcmd "s#%common.jwt.issuer%#issuer-name-for-jwt#g" $TEST_APP_CONF

eval $sedcmd "s#%common.messaging.domain%#some.domain.com#g" $TEST_APP_CONF
eval $sedcmd "s#%common.messaging.host%#some.host.com#g" $TEST_APP_CONF
eval $sedcmd "s#%common.messaging.service%#some.service.com#g" $TEST_APP_CONF
eval $sedcmd "s#%common.messaging.admin_username%#admin#g" $TEST_APP_CONF
eval $sedcmd "s#%common.messaging.admin_password%#get_jwt_hash_for_admin_admin#g" $TEST_APP_CONF

eval $sedcmd "s#%common.orientdb.url%#memory:test#g" $TEST_APP_CONF
eval $sedcmd "s#%common.orientdb.user%#admin#g" $TEST_APP_CONF
eval $sedcmd "s#%common.orientdb.password%#admin#g" $TEST_APP_CONF

eval $sedcmd "s#%common.aws.s3_bucket%#aws-bucket-name#g" $TEST_APP_CONF

eval $sedcmd "s#%common.twilio.accountsid%#twilio-id-hash#g" $TEST_APP_CONF
eval $sedcmd "s#%common.twilio.authtoken%#twilio-token-hash#g" $TEST_APP_CONF
eval $sedcmd "s#%common.twilio.sender%#+15005550006#g" $TEST_APP_CONF

eval $sedcmd "s#%common.notification.region%#aws-region#g" $TEST_APP_CONF
eval $sedcmd "s#%common.notification.android%#test-ARN-for-GCM...arn:aws:sns:some-aws-region:some-sns-id:app/GCM/some-app-name#g" $TEST_APP_CONF
eval $sedcmd "s#%common.notification.ios%#test-ARN-for-APNS...arn:aws:sns:some-aws-region:some-sns-id:app/APNS_SANDBOX/some-app-name#g" $TEST_APP_CONF

eval $sedcmd "s#%common.google_api_key_password%#api-key-hash#g" $TEST_APP_CONF