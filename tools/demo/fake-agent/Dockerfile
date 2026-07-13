FROM python:3.13-alpine

WORKDIR /app
COPY fake_agent.py ./

RUN addgroup -S app && adduser -S -G app app

USER app
EXPOSE 8000
CMD ["python", "fake_agent.py"]
