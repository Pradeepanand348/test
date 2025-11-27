#!/usr/bin/env python
##############################################################################
#  Copyright (c) 2018 Pulse Secure
##############################################################################

import boto3
import subprocess
import time
import os
import sys
import argparse
from aws_utils import S3Bucket, S3Object 

SD_EC2_INSTANCE = 'i-0d65fbe2fb92b66ee'
SD_EC2_INSTANCE_PKEY = 'sd-ami-build.pem'
SD_EC2_INSTANCE_REGION = 'eu-west-1'
SD_EC2_USER_PREFIX = 'ubuntu@'
SD_EC2_BUILD_DIRECTORY = '~'
SD_EC2_AWS_CLI_CONFIG_DIRECTORY = '~/.aws'
AWS_CMD='aws'

def ssh(ip, private_key, command):
    ''' Run a command using SSH '''
    command_array = ['ssh', 
        '-oStrictHostKeyChecking=no',
        '-i',
        private_key,
        SD_EC2_USER_PREFIX + ip
        ] 
    command_array.extend(command.split())
    subprocess.check_call(command_array, stderr=subprocess.STDOUT)

def wait_for_ssh(ip, private_key):
    ''' Wait for SSH service to be up'''
    print 'Waiting for SSH to be reachable for ', ip
    for i in range(300):
        try:
            ssh(ip, private_key, 'true')
        except:
            time.sleep(1)
        else:
            break
    else:
        raise RuntimeError('SSH unreachable for ' + ip)
    print 'SSH is reachable'

def copy_files(region, ip, private_key, file_list):
    ''' Copy a list of files to the EC2 instance through S3 buckets '''
    with S3Bucket(region) as bucket_name:
        for file_name in file_list:
            with S3Object(file_name, bucket_name, ec2=True) as object_name:
                print 'Downloading ', object_name, 'from s3 bucket to ec2'
                opts = '' if sys.stdout.isatty() else '--no-progress '
                ssh(ip, private_key, 
                    '{aws} s3 cp {opts} s3://{bucket}/{file_name} {file_name}'.format(
                        aws=AWS_CMD, opts=opts, bucket=bucket_name, file_name=object_name))

class EC2Instance(object):                
    '''Context manager responsible for starting and stopping the 
    AWS instance used for build.
    '''

    def __init__(self, instance_name):
        self.instance_name = instance_name

    def __enter__(self):
        ec2 = boto3.resource('ec2')
        print 'Initialising ', self.instance_name
        self.instance = ec2.Instance(self.instance_name)
        print 'Current state:', self.instance.state['Name']
        if self.instance.state['Name'] != 'running':
            print 'Starting'
            self.instance.start()
            print 'Waiting until instance is running..'
            self.instance.wait_until_running()
            self.instance = ec2.Instance(self.instance_name)
        instance_ip = self.instance.public_ip_address
        print 'Public IP:', instance_ip
        print 'State:', self.instance.state['Name']
        return self.instance

    def __exit__(self, *args):
        print 'Stopping', self.instance_name
        self.instance.stop()
        self.instance.wait_until_stopped()
        print 'Stopped'
        
def main():
    parser = argparse.ArgumentParser(description=
            "Convert a development OVA to a development AMI")
    parser.add_argument("ova", help="Path to the OVA to be converted")
    parser.add_argument("--build", help="Build name or number", default='DEBUG')
    parser.add_argument("--region", help="AWS region", default='eu-west-1')
    parser.add_argument("--live", help="Upload to live account", default=False, action="store_true")
    args = parser.parse_args()
    files_directory = os.path.dirname(sys.argv[0])
    private_key = os.path.join(files_directory, SD_EC2_INSTANCE_PKEY)
    # If using live account, always upload to us-east-1.
    if args.live:
        args.region = 'us-east-1'

    with EC2Instance(SD_EC2_INSTANCE) as instance:
        instance_ip = instance.public_ip_address
        wait_for_ssh(instance_ip, private_key)

        print 'Cleaning build directory'
        ssh(instance_ip, private_key, 'rm -rf %s/*' % SD_EC2_BUILD_DIRECTORY)

        print 'Cleaning AWS CLI config'
        ssh(instance_ip, private_key, 'rm -rf %s/*' % SD_EC2_AWS_CLI_CONFIG_DIRECTORY)

        print 'Copying files'
        file_list = [
            os.path.join(files_directory, 'aws_utils.py'),
            os.path.join(files_directory, 'ova_to_ami.py'),
            os.path.expanduser(args.ova)
        ]
        # For live situation we need to copy appropriate credentials and config.
        if args.live:
            file_list.extend([
                os.path.join(files_directory, 'aws_live_config'),
                os.path.join(files_directory, 'aws_live_credentials'),
            ])
        copy_files(SD_EC2_INSTANCE_REGION, instance_ip,
            private_key, file_list)
        ssh(instance_ip, private_key, 'ls -l')

        # The machine has an appropriate IAM role for non-live situations.
        if args.live:
            print 'Configuring AWS CLI'
            ssh(instance_ip, private_key, 'mv aws_live_config %s/config' %
                    SD_EC2_AWS_CLI_CONFIG_DIRECTORY)
            ssh(instance_ip, private_key, 'mv aws_live_credentials %s/credentials' %
                    SD_EC2_AWS_CLI_CONFIG_DIRECTORY)

        cmd = 'python ova_to_ami.py --ec2'
        if args.live:
            cmd += ' --live'
        cmd += ' --region=' + args.region
        cmd += ' --build=' + args.build
        cmd += ' --region=' + args.region
        cmd += ' ' + os.path.basename(os.path.expanduser(args.ova))
        ssh(instance_ip, private_key, cmd)

if __name__ == '__main__':
    main()
