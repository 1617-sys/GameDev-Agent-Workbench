FROM python:3.13-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --disable-pip-version-check -r requirements.txt \
    && groupadd --system app \
    && useradd --system --gid app --create-home app

COPY --chown=app:app app ./app

EXPOSE 8000
USER app
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
