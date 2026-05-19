import os
from fastapi_mail import ConnectionConfig, FastMail, MessageSchema, MessageType
from pydantic import EmailStr
from dotenv import load_dotenv

load_dotenv()

conf = ConnectionConfig(
    MAIL_USERNAME = os.getenv("MAIL_USERNAME"),
    MAIL_PASSWORD = os.getenv("MAIL_PASSWORD"),
    MAIL_FROM = os.getenv("MAIL_FROM"),
    MAIL_PORT = int(os.getenv("MAIL_PORT", 587)),
    MAIL_SERVER = os.getenv("MAIL_SERVER"),
    MAIL_FROM_NAME = os.getenv("MAIL_FROM_NAME"),
    MAIL_STARTTLS = True,
    MAIL_SSL_TLS = False,
    USE_CREDENTIALS = True,
    VALIDATE_CERTS = True
)

async def send_verification_email(email: EmailStr, code: str):
    try:
        message = MessageSchema(
            subject="LESCO Smart Home - Verification Code",
            recipients=[email],
            body=f"Your verification code is: {code}",
            subtype=MessageType.plain
        )
        fm = FastMail(conf)
        await fm.send_message(message)
        print(f"DEBUG: Email sent to {email}")
    except Exception as e:
        print(f"DEBUG ERROR: Failed to send email to {email}: {e}")

async def send_reset_password_email(email: EmailStr, code: str):
    try:
        message = MessageSchema(
            subject="LESCO Smart Home - Reset Password",
            recipients=[email],
            body=f"Your password reset code is: {code}",
            subtype=MessageType.plain
        )
        fm = FastMail(conf)
        await fm.send_message(message)
        print(f"DEBUG: Reset email sent to {email}")
    except Exception as e:
        print(f"DEBUG ERROR: Failed to send reset email to {email}: {e}")
