#!/usr/bin/env python3
"""Guangya SMS login helper with resumable local state.

Credentials are written to .local/guangya_credentials.json, which is ignored by
Git. The script intentionally uses only the Python standard library so it can run
in constrained agent environments.
"""

from __future__ import annotations

import argparse
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from hashlib import md5
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
LOCAL_DIR = ROOT / ".local"
STATE_FILE = LOCAL_DIR / "guangya_login_state.json"
CREDENTIALS_FILE = LOCAL_DIR / "guangya_credentials.json"
ACCOUNT_BASE = "https://account.guangyapan.com"
API_BASE = "https://api.guangyapan.com"
CLIENT_ID = "aMe-8VSlkrbQXpUR"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
)


def ensure_local_dir() -> None:
    LOCAL_DIR.mkdir(parents=True, exist_ok=True)


def generate_did() -> str:
    return md5(os.urandom(16)).hexdigest()


def traceparent() -> str:
    return f"00-{secrets.token_hex(16)}-{secrets.token_hex(8)}-01"


def load_json(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    with path.open("r", encoding="utf-8") as fp:
        return json.load(fp)


def save_json(path: Path, payload: Any) -> None:
    ensure_local_dir()
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as fp:
        json.dump(payload, fp, ensure_ascii=False, indent=2, sort_keys=True)
        fp.write("\n")
    tmp.replace(path)


def account_headers(device_id: str, extra: dict[str, str] | None = None) -> dict[str, str]:
    headers = {
        "accept": "*/*",
        "content-type": "application/json",
        "origin": "https://www.guangyapan.com",
        "referer": "https://www.guangyapan.com/",
        "user-agent": USER_AGENT,
        "x-client-id": CLIENT_ID,
        "x-client-version": "0.0.1",
        "x-device-id": device_id,
        "x-device-model": "chrome%2F147.0.0.0",
        "x-device-name": "PC-Chrome",
        "x-device-sign": f"wdi10.{device_id}{secrets.token_hex(16)}",
        "x-net-work-type": "NONE",
        "x-os-version": "MacIntel",
        "x-platform-version": "1",
        "x-protocol-version": "301",
        "x-provider-name": "NONE",
        "x-sdk-version": "9.0.2",
    }
    if extra:
        headers.update(extra)
    return headers


def api_headers(device_id: str, token: str | None = None) -> dict[str, str]:
    headers = {
        "accept": "application/json, text/plain, */*",
        "content-type": "application/json",
        "did": device_id,
        "dt": "4",
        "origin": "https://www.guangyapan.com",
        "referer": "https://www.guangyapan.com/",
        "traceparent": traceparent(),
        "user-agent": USER_AGENT,
    }
    if token:
        headers["authorization"] = f"Bearer {token}"
    return headers


def post_json(url: str, headers: dict[str, str], body: dict[str, Any] | None = None) -> Any:
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code} {url}\n{raw}") from exc
    if not raw:
        return {}
    return json.loads(raw)


def first_token(payload: Any, *names: str) -> str | None:
    if isinstance(payload, dict):
        for name in names:
            value = payload.get(name)
            if isinstance(value, str) and value:
                return value
        for value in payload.values():
            nested = first_token(value, *names)
            if nested:
                return nested
    if isinstance(payload, list):
        for item in payload:
            nested = first_token(item, *names)
            if nested:
                return nested
    return None


def start(args: argparse.Namespace) -> None:
    state = load_json(STATE_FILE, {})
    device_id = state.get("device_id") or generate_did()
    phone = args.phone or state.get("phone")
    if not phone:
        raise SystemExit("Provide --phone, for example: --phone '+86 13800138000'")

    init_body: dict[str, Any] = {
        "client_id": CLIENT_ID,
        "action": "POST:/v1/auth/verification",
        "device_id": device_id,
        "meta": {"phone_number": phone},
    }
    if args.captcha_token:
        init_body["captcha_token"] = args.captcha_token

    init_result = post_json(
        f"{ACCOUNT_BASE}/v1/shield/captcha/init",
        account_headers(device_id),
        init_body,
    )
    captcha_token = first_token(init_result, "captcha_token", "captchaToken") or args.captcha_token
    challenge_url = first_token(init_result, "url", "captcha_url", "captchaUrl")
    if not captcha_token:
        save_json(
            STATE_FILE,
            {
                "stage": "captcha",
                "phone": phone,
                "device_id": device_id,
                "init_result": init_result,
                "updated_at": time.time(),
            },
        )
        print(json.dumps(init_result, ensure_ascii=False, indent=2))
        print("\nCaptcha token was not returned.")
        if challenge_url:
            print(f"Open the verification URL, then rerun start with --captcha-token:\n{challenge_url}")
        raise SystemExit(2)

    send_result = post_json(
        f"{ACCOUNT_BASE}/v1/auth/verification",
        account_headers(device_id, {"x-captcha-token": captcha_token}),
        {"phone_number": phone, "target": args.target, "client_id": CLIENT_ID},
    )
    verification_id = first_token(send_result, "verification_id", "verificationId")
    if not verification_id:
        print(json.dumps(send_result, ensure_ascii=False, indent=2))
        raise SystemExit("SMS verification_id was not returned.")

    save_json(
        STATE_FILE,
        {
            "stage": "sms_sent",
            "phone": phone,
            "device_id": device_id,
            "captcha_token": captcha_token,
            "verification_id": verification_id,
            "init_result": init_result,
            "send_result": send_result,
            "updated_at": time.time(),
        },
    )
    print("SMS sent. The resumable state has been saved locally.")
    print(f"Finish with: python3 tools/guangya_login.py finish --code <SMS_CODE>")


def finish(args: argparse.Namespace) -> None:
    state = load_json(STATE_FILE, {})
    required = ["phone", "device_id", "captcha_token", "verification_id"]
    missing = [key for key in required if not state.get(key)]
    if missing:
        raise SystemExit(f"Login state is incomplete, missing: {', '.join(missing)}. Run start first.")

    device_id = state["device_id"]
    code = args.code.strip()
    verify_result = post_json(
        f"{ACCOUNT_BASE}/v1/auth/verification/verify",
        account_headers(device_id),
        {
            "verification_id": state["verification_id"],
            "verification_code": code,
            "client_id": CLIENT_ID,
        },
    )
    verification_token = first_token(verify_result, "verification_token", "verificationToken")
    if not verification_token:
        print(json.dumps(verify_result, ensure_ascii=False, indent=2))
        raise SystemExit("verification_token was not returned.")

    signin_result = post_json(
        f"{ACCOUNT_BASE}/v1/auth/signin",
        account_headers(device_id, {"x-captcha-token": state["captcha_token"]}),
        {
            "verification_code": code,
            "verification_token": verification_token,
            "username": state["phone"],
            "client_id": CLIENT_ID,
        },
    )
    access_token = first_token(signin_result, "access_token", "accessToken")
    refresh_token = first_token(signin_result, "refresh_token", "refreshToken")
    expires_in = signin_result.get("expires_in") if isinstance(signin_result, dict) else None
    expires_at = time.time() + float(expires_in) if isinstance(expires_in, (int, float)) else None
    if not access_token:
        print(json.dumps(signin_result, ensure_ascii=False, indent=2))
        raise SystemExit("access_token was not returned.")

    credentials = {
        "phone": state["phone"],
        "device_id": device_id,
        "access_token": access_token,
        "refresh_token": refresh_token,
        "expires_at": expires_at,
        "signin_result": signin_result,
        "verify_result": verify_result,
        "updated_at": time.time(),
    }
    save_json(CREDENTIALS_FILE, credentials)
    state["stage"] = "done"
    state["updated_at"] = time.time()
    save_json(STATE_FILE, state)
    print(f"Credentials saved to {CREDENTIALS_FILE.relative_to(ROOT)}")
    probe(argparse.Namespace(limit=args.limit))


def refresh_credentials(credentials: dict[str, Any]) -> dict[str, Any]:
    refresh_token = credentials.get("refresh_token")
    if not refresh_token:
        raise SystemExit("No refresh_token in credentials.")
    device_id = credentials.get("device_id") or generate_did()
    result = post_json(
        f"{ACCOUNT_BASE}/v1/auth/token",
        account_headers(device_id, {"x-action": "401"}),
        {"client_id": CLIENT_ID, "grant_type": "refresh_token", "refresh_token": refresh_token},
    )
    access_token = first_token(result, "access_token", "accessToken")
    if not access_token:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        raise SystemExit("Token refresh failed.")
    expires_in = result.get("expires_in") if isinstance(result, dict) else None
    credentials.update(
        {
            "access_token": access_token,
            "refresh_token": first_token(result, "refresh_token", "refreshToken") or refresh_token,
            "expires_at": time.time() + float(expires_in) if isinstance(expires_in, (int, float)) else None,
            "refresh_result": result,
            "updated_at": time.time(),
        }
    )
    save_json(CREDENTIALS_FILE, credentials)
    return credentials


def credentials_or_exit() -> dict[str, Any]:
    credentials = load_json(CREDENTIALS_FILE, None)
    if not credentials:
        raise SystemExit("No local credentials. Run start and finish first.")
    if credentials.get("expires_at") and time.time() >= float(credentials["expires_at"]) - 60:
        credentials = refresh_credentials(credentials)
    return credentials


def probe(args: argparse.Namespace) -> None:
    credentials = credentials_or_exit()
    token = credentials["access_token"]
    device_id = credentials.get("device_id") or generate_did()
    user = post_json(
        f"{ACCOUNT_BASE}/v1/user/me",
        account_headers(device_id, {"authorization": f"Bearer {token}"}),
        None,
    )
    root = post_json(
        f"{API_BASE}/userres/v1/file/get_file_list",
        api_headers(device_id, token),
        {"parentId": "", "page": 0, "pageSize": args.limit, "orderBy": 0, "sortType": 0},
    )
    videos = post_json(
        f"{API_BASE}/userres/v1/file/get_file_list",
        api_headers(device_id, token),
        {
            "parentId": "*",
            "page": 0,
            "pageSize": args.limit,
            "orderBy": 3,
            "sortType": 1,
            "fileTypes": [2],
            "resType": 1,
            "needPlayRecord": True,
        },
    )
    print(
        json.dumps(
            {"user": user, "root_files": root, "videos": videos},
            ensure_ascii=False,
            indent=2,
        )
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Resumable Guangya SMS login helper")
    subparsers = parser.add_subparsers(dest="command", required=True)

    start_parser = subparsers.add_parser("start", help="send SMS and persist login state")
    start_parser.add_argument("--phone", help="phone number with country code, e.g. '+86 13800138000'")
    start_parser.add_argument("--captcha-token", help="captcha token if the init endpoint requires manual challenge")
    start_parser.add_argument("--target", default="ANY", help="SMS target, default: ANY")
    start_parser.set_defaults(func=start)

    finish_parser = subparsers.add_parser("finish", help="finish login with SMS code")
    finish_parser.add_argument("--code", required=True, help="SMS verification code")
    finish_parser.add_argument("--limit", type=int, default=20, help="probe page size after login")
    finish_parser.set_defaults(func=finish)

    probe_parser = subparsers.add_parser("probe", help="print user/root/video API samples using saved credentials")
    probe_parser.add_argument("--limit", type=int, default=20, help="probe page size")
    probe_parser.set_defaults(func=probe)

    refresh_parser = subparsers.add_parser("refresh", help="refresh saved token")
    refresh_parser.set_defaults(func=lambda _args: print(json.dumps(refresh_credentials(credentials_or_exit()), ensure_ascii=False, indent=2)))

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)

