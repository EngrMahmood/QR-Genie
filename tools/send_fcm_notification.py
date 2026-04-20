"""
Simple script to send an FCM notification using the legacy HTTP API.

Usage:
  - Set your FCM server key in the SERVER_KEY variable or export FCM_SERVER_KEY env var
  - Call: python send_fcm_notification.py

Note: For production use prefer the HTTP v1 API with OAuth service account.
"""
import os
import json
import requests

SERVER_KEY = os.environ.get('FCM_SERVER_KEY', '')
if not SERVER_KEY:
    print('Set FCM_SERVER_KEY environment variable to your Firebase server key (from project settings).')
    exit(1)

# Send to topic 'releases' (clients should subscribe to this topic)
payload = {
    "to": "/topics/releases",
    "notification": {
        "title": "QR Genie update",
        "body": "A new version of QR Genie is available. Tap to open Play Store.",
    },
    "data": {
        "click_action": "FLUTTER_NOTIFICATION_CLICK"
    }
}

headers = {
    'Authorization': 'key=' + SERVER_KEY,
    'Content-Type': 'application/json'
}

resp = requests.post('https://fcm.googleapis.com/fcm/send', headers=headers, data=json.dumps(payload))
print('status:', resp.status_code)
print(resp.text)

