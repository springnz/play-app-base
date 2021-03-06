# Play-App-Base

Play-App-Base provides basic functionality for a Play Framework based mobile/web app.

Existing features can be used as is or easily build upon.

## Features

* graph DB persistence - [OrientDB](http://orientdb.com/)

* messaging - [ejabberd](https://www.ejabberd.im/)

* authentication - using [JWT](https://jwt.io/) in HTTP headers.

* location - nearby users, location suggestions, reverse lookup

* push notification - via [AWS SNS](https://aws.amazon.com/sns/)

* SMS - via [Twilio](https://www.twilio.com/)

* file upload - to [AWS S3](https://aws.amazon.com/s3/)

* health check - probes OrientDB, ejabberd and S3 services

## Usage

Tests need a config file to be generated first via the `scripts/applicationConf.sh` script. Make sure you edit all the config values accordingly.

Common test classes are in `scr/main/scala/test` package and can/should be used (extended) in your project.

## Play Framework

This project doesn't use the Play plugin directly to not interfere with Play enabled apps that use this project as a dependancy. It rather depends on Play artifacts directly and uses a [custom router](src/test/scala/ylabs/play/common/RequestHandler.scala) for tests.