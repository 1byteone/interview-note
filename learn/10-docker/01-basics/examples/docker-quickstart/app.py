from flask import Flask

app = Flask(__name__)

@app.route('/')
def hello():
    return "Hello from Docker!"

@app.route('/health')
def health():
    return "OK", 200

if __name__ == '__main__':
    # 监听 0.0.0.0 确保容器外部可访问
    app.run(host='0.0.0.0', port=5000)