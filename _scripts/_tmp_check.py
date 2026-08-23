# -*- coding: utf-8 -*-
path = '2-learning/stacks/05-fastapi/README.md'
with open(path, encoding='utf-8') as f:
    content = f.read()

# 检查文件最后100 字符
print('Last 200 chars:')
print(repr(content[-200:]))
print()

# 检查是否以 ```结尾
if content.rstrip().endswith('```'):
    print('文件以 ``` 结尾')
else:
    print('文件不以 ``` 结尾')

# 检查 fences
fences = content.count('```')
print(f'fences: {fences} (even: {fences % 2 == 0})')
