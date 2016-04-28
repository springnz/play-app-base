#!/usr/bin/env python

# Copyright 2014 Gregor Uhlenheuer
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# To use in ejabberd, copy script to /path/to/ejabberd/conf/
# set ejaberd.yml to
# auth_method: external
# extauth_program: "/path/to/ejabberd/conf/banjo_auth.py service url --endpoint user/info" -l /path/to/ejabberd/logs

import argparse
import json
import logging
import os
import struct
import sys
from urllib2 import Request, urlopen, URLError


#
# DEFAULTS AND CONSTANTS
#

DEFAULT_LOG_DIR = '/var/log/ejabberd'
FALLBACK_URL = 'http://localhost:8000/auth/'
FALLBACK_ENDPOINT = 'user/info'
HEADERS = {
    'Content-Type': 'application/json',
    'Accept': 'application/json' }


#
# CLASS DEFINITIONS
#

class EjabberdError(Exception):
    '''Exception class that holds ejabberd related errors.'''

    def __init__(self, ex):
        self.ex = ex

    def __str__(self):
        return repr(self.ex)

class ApiHandler:
    '''
    Class to execute HTTP requests to process
    the authentication orders from ejabberd.
    '''

    def __init__(self, url, headers):
        '''Initialize an ApiHandler instance.'''

        self.url = url
        self.headers = headers

    def call(self, call, token, data):
        '''
        Call the specified authentication API using the
        urllib2 library functions.
        '''

        url = '%s/%s' % (self.url, call)
        headersWithAuth = self.headers.copy()
        headersWithAuth.update({"Authorization": "Bearer {0}".format(token)})

        req = Request(url)
        req.add_header("Authorization", "Bearer {0}".format(token));
        req.add_header("Accept", "application/json")
        logging.debug('Calling {0} with method {1} and headers {2}'.format(url, req.get_method(), headersWithAuth))
        result = {}
        try:
			'''Python's http api is a bit ugly - it will always throw a URLError on any failure. If nothing happens, that means it's ok... '''
			response = urlopen(req)
			'''data': json.load(response),'''
			result = { 'status': 200}
        except URLError as e:
    		if hasattr(e, 'reason'):
        		logging.warn('We failed to reach a server. Reason: {0}'.format(e.reason))
        		result = {'status': e.code, 'message': e.reason}
    		elif hasattr(e, 'code'):
        		logging.warn('The server couldn\'t fulfill the request. Code: {0}'.format(e.code))
        		result = {'status': e.code, 'message': e.read()}

        return result

class EjabberdAuth:
    '''
    Class that encapsulates the ejabberd authentication logic.
    '''

    def __init__(self, url, endpoint, headers, handler=None):
        '''
        Initialize a new EjabberdAuth instance.
        '''
        self.url = url
        self.headers = headers
        self.endpoint = endpoint

        if handler is None:
            self.handler = ApiHandler(url, headers)
        else:
            self.handler = handler

    @staticmethod
    def make_jid(user, host):
        '''Build a JID using the given user and host'''

        return '%s@%s' % (user, host)

    def __from_ejabberd(self):
        '''
        Listen on stdin and read input data sent from the
        connected ejabberd instance.
        '''

        try:
            input_length = sys.stdin.read(2)

            if len(input_length) is not 2:
                logging.warn('ejabberd called with invalid input')
                return None

            (size,) = struct.unpack('>h', input_length)
            result = sys.stdin.read(size)

            logging.debug('Read %d bytes: %s', size, result)

            return result.split(':')
        except IOError:
            raise EjabberdError('Failed to read from ejabberd via stdin')

    def __to_ejabberd(self, success):
        '''
        Convert the input data into an ejabberd compatible
        format and send it to stdout.
        '''

        answer = 1 if success else 0
        token = struct.pack('>hh', 2, answer)

        sys.stdout.write(token)
        sys.stdout.flush()

        logging.debug('Returned %s success', 'with' if success else 'without')

    def __call_api(self, call, token, data):
        '''
        Call the JSON compatible API handler with the specified data
        and parse the response for a success.
        '''

        body = json.dumps(data)
        logging.debug('Calling endpoint')
        result = self.handler.call(call, token, body)

        success = result['status'] == 200

        logging.debug('Success {0}'.format(success))

        if not success:
            msg = result['message']
            logging.warn('Call to API returned without success: code: {0}'.format(result['status']) + ' message: ' + msg)

        return success

    def __auth(self, username, server, password):
        '''Try to authenticate the user with the specified password.'''

        logging.debug('Processing "auth"')

        token = password
        data = None

        return self.__call_api(self.endpoint, token, data)

    def __isuser(self, username, server):
        return False

    def __setpass(self, username, server, password):
        return False

    def loop(self):
        '''
        Start the endless loop that reads on stdin and passes
        the authentication results to stdout towards the
        connected ejabberd instance.
        '''

        while True:
            try:
                data = self.__from_ejabberd()
            except KeyboardInterrupt:
                logging.info('Terminating by user input')
                break
            except EjabberdError, err:
                logging.warn('Input error: ' + err)
                break

            success = False

            cmd = 'none'
            if data != None:
            	cmd = data[0]

            if cmd == 'none':
                success = False
            elif cmd == 'auth':
                success = self.__auth(data[1], data[2], data[3])
            elif cmd == 'isuser':
                success = self.__isuser(data[1], data[2])
            elif cmd == 'setpass':
                success = self.__setpass(data[1], data[2], data[3])
            elif cmd == 'tryregister':
                success = True
            else:
                logging.warn('Unhandled ejabberd cmd "%s"', cmd)

            self.__to_ejabberd(success)


def get_args():
    '''
    Parse some basic configuration from command line arguments.
    '''

    # build command line argument parser
    desc = 'ejabberd authentication script'
    parser = argparse.ArgumentParser(description=desc)

    # base url
    parser.add_argument('url',
            nargs='?',
            metavar='URL',
            default=FALLBACK_URL,
            help='base URL (default: %(default)s)')

    parser.add_argument('-e', '--endpoint',
    		default=FALLBACK_ENDPOINT ,
    		help='endpoint name relative to base url (default: %(default)s)')

    # log file location
    parser.add_argument('-l', '--log',
            default=DEFAULT_LOG_DIR,
            help='log directory (default: %(default)s)')

    # debug log level
    parser.add_argument('-d', '--debug',
            action='store_const', const=True,
            help='toggle debug mode')

    args = vars(parser.parse_args())

    return args['url'], args['endpoint'], args['debug'], args['log']


if __name__ == '__main__':
    URL, ENDPOINT, DEBUG, LOG = get_args()

    LOGFILE = LOG + '/extauth.log'
    LEVEL = logging.DEBUG if DEBUG else logging.INFO
    PID = str(os.getpid())
    FMT = '[%(asctime)s] ['+PID+'] [%(levelname)s] %(message)s'

    # redirect stderr
    ERRFILE = LOG + '/extauth.err'
    sys.stderr = open(ERRFILE, 'a+')

    # configure logging
    logging.basicConfig(level=LEVEL, format=FMT, filename=LOGFILE)

    logging.info('Starting ejabberd auth script')
    logging.info('Using %s as base URL', URL)
    logging.info('Using %s as endpoint', ENDPOINT)
    logging.info('Running in %s mode', 'debug' if DEBUG else 'release')

    EJABBERD = EjabberdAuth(URL, ENDPOINT, HEADERS)
    EJABBERD.loop()

    logging.warn('Terminating ejabberd auth script')

# vim: set et sw=4 sts=4 tw=80: