import json, sys, io, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

lines = """realpython/python-guide	29770
ombharatiya/ai-system-design-guide	2698
liaokongVFX/MCP-Chinese-Getting-Started-Guide	3560
adongwanai/AgentGuide	8639
bcefghj/ai-agent-interview-guide	2119
agentsmd/agents.md	23783
Genesis-Embodied-AI/RoboGen	1223
libukai/awesome-agent-skills	4998
0xNyk/awesome-hermes-agent	5405
FlorianBruniaux/claude-code-ultimate-guide	5781
zebbern/claude-code-guide	4585
heilcheng/awesome-agent-skills	6126
dair-ai/Prompt-Engineering-Guide	77672
yeasy/harness_engineering_guide	115
walkinglabs/awesome-harness-engineering	3891
slowmist/openclaw-security-practice-guide	2857
by123456by/AI-Shell	15
jwangkun/hermes-agent-guide	650
freestylefly/CodexGuide	3234
mshadmanrahman/claudecode-guide	34
flaqai/deepeseek-harness-guide	13
Marcos-wu/ai-agent-daily-mentor	133
lingxling/awesome-skills-cn	257
didilili/ai-agents-from-zero	3974
Paidax01/math-curve-loaders	1983
woaiys3/deepseek-harness-android-app	81
paperclipai/paperclip	79106
Francis-Xavier-code/tiktok-douyin-dl	24
Francis-Xavier-code/gqy	1
rauldotgit/E-Commerce-Store	34
bbulakh/tailwind-ecommerce	81
Yeachan-Heo/oh-my-claudecode	38712
volcengine/OpenViking	31711
fredalxin/dsh-solo-thinking	19
oil-oil/dsh-oil-creator	110
zhu1090093659/dsh-web-ui	5446
alibaba/open-code-review	21083
crafter-station/petdex	3948
HanaAyane/remielle-codex-pet	318
qq547276542/Agriculture_KnowledgeGraph	4387
B666T/DesktopPetGenerator	4
jiji262/douyin-downloader	9436
HFrost0/bilix	1785
dataiscool/docu-crawler	6
NanmiCoder/CrawlerTutorial	4539
NanmiCoder/MediaCrawler	63419
JoeanAmier/XHS-Downloader	12442
Andy-SoulShell/xhs-downloader	1
anysearch-team/anysearch-dsh	185
AlleyBo55/doraemon	43
cskwork/pet-mochi	1
morettt/my-neuro	1341
Open-LLM-VTuber/Open-LLM-VTuber	13402
emilkowalski/skills	31400
windyslime/DeepSee	4
fuzhengwei/WaLiOffice	23
ccch1mneyyy/dsh-TUI	2268
huiliyi37/Tianshu-harness	246
openma-ai/Martty	47
pig-mesh/deepseek4j	755
deepseek-ai/DeepSeek-Coder	24191
eli64s/readme-ai	2974
jezweb/claude-skills	975
1byteone/1byteone.github.io	1
mmistakes/minimal-mistakes	13556
HugoBlox/hugo-theme-developer-portfolio	70
ivansaul/personal-portfolio	167
varadbhogayata/varadbhogayata.github.io	1528
hwttop5/tabbit2api	91
kamcdev/Jsoft_markbot_mjbcore	6
Tabbit-Browser/dsh-tabbit	92
Shivam-Shukl/Amazon-E-commerce-Agent-RAG-Based-AI-Product-Recommender	2
Triveni1706/AI-Ecommerce-Product-Intelligence	1
yuniko-software/bge-m3-qdrant-sample	41
marqo-ai/marqo	5023
TOPDEV99999/ai-ShopMind	35
nitin27may/e-commerce-agents	18
upsidelab/enthusiast	168
linkfox-ai/linkfox-skills	84
nexscope-ai/eCommerce-Skills	759
FlatNineOrg/ecommerce-skills	6
FlagOpen/FlagEmbedding	12070
2701/findegil	17
yuniko-software/kernel-memory-ecommerce-sample	9
elastic/elasticsearch-labs	1121
aws-samples/intelligent-product-search-with-llm	14
367280/Shop-Mind	5
mymagicpower/AIAS	988
bradeGithub/DSH-Plugins-Marketplace	134
GCWing/BitFun	1803
shaomang/mtnode-aio	36
acheAIsuiyimen/ache-life-to-comic-skill	43
xing-kj/XingGraph	13
semantica-agi/semantica	10106
macro-inc/macro	3949
cathrynlavery/diagram-design	25058
Francis-Xavier-code/dsh-balance-plugin	53
fxstein/ai-todo	15
open-mercato/cezar	170
omdsh-dev/DSH-better-sidebar	2584
ChisaAlter/Deepseek-Harness-Desktop	133
Small-tailqwq/dsh-deep-whale	1565
deepseek-ai/deepseek-harness	181824
larashero3-dotcom/lieflat-html-design	78
larashero3-dotcom/lieflat-charts	1625
Kolzsticks/Free-Ecommerce-Products-Api	13
rebrowser/shopee-dataset	11
tencent-ailab/hok_env	899
saleor/storefront	1588
spree/spree	15633
gz-yami/mall4j	5212
vuestorefront/storefront-ui	2508
rucliujn/JDsearch	48
bright-cn/eCommerce-dataset-samples	3
laogou717/md-wechat	199
UniqueYu8988/XiaoLu	6
HirezmingD/Knowe-agent-groupchat	154
yuque/yuque-cli	16
mergedao/mcp-jobs	124
crewAIInc/crewAI	57440
microsoft/autogen	60565
RandyChen1985/nanzi-ai-agent-platform	120
tedhappy/ragflow-admin	262
zstar1003/ragflow-plus	1411
infiniflow/ragflow	89001
Mintplex-Labs/anything-llm	65030
vadimdemedes/ink	39708
The-Milky-Way-traveller/yanxi-single	4
ktaletsk/learn-codebase	47
SpringSunYY/LZ-litchi	160
xiaolinbaba/xiaolin-madopic	206
baidu/Unlimited-OCR	24321
oil-oil/see-skill	145
jinruozai/HTML-Light-Demo	97
rothgar/awesome-tuis	20267
nhn/tui.editor	18007
fazgal0/free-sms-receivers	1006
xyiqq/china-heritage-3d	68
atomgit-atomcode/atomcode	187
opensquilla/opensquilla	6633
qianjiazheng2023/xiaohongshu-article-cards	29
gzyxds/buidai	15
DQ913/wechat_content_factory	3
mf-yang/toutiao-ops	40
yan2959088709/InkAI-	80
toki-plus/ai-ttv-workflow	126
RyanYipeng/SyncCaster	485
dongdaxiaofeizao/WeCMS	114
nashsu/Viral_Writer_Skill	736
NanmiCoder/cc-haha	14183
PatrickJS/awesome-cursorrules	40634
public-apis/public-apis	468073
remiliacn/qqBot	677
buainoai/awesome-clawdbot-skills	48
lss233/kirara-ai	18954
langsearch-ai/langsearch	59
Hacker233/resume-design	3909
decolua/9router	26023
zzsting88/relayAPI	4105
AnRkey/Grok-Desktop	203
assistant-ui/assistant-ui	11768
DatoBHJ/grok-clone	15
superagent-ai/grok-cli	3425
chenyme/grok2api	7505
Drok1015/marvis-office	16
ripienaar/free-for-dev	133312
winterBI/marvis-office	33
Nutlope/hallmark	26402
msitarzewski/agency-agents	147214
Cocoon-AI/architecture-diagram-generator	6987
Blankeos/crabcode	39
Dicklesworthstone/pi_agent_rust	1630
op7418/CodePilot	6415
oil-oil/oil-cover	194
rzashakeri/beautify-github-profile	12486
devoink/ossput	1
liangdabiao/ecom-details-image-ui	46
Yacey/agnes-ai-generation-skill	402
drfccv/mcp-server-12306	368
itwanger/MonkeyCode	2
datawhalechina/hello-agents	74134
xiaolincoder/CS-Base	18280
Snailclimb/JavaGuide	157936
wuyoscar/GPT-Image2-Skill	4804
element-plus-x/ruoyi-element-ai	483
kakawaa/OnCall-Agent	52
Tianba0116/mewcode	12
yophon/yo-agent	4
hesreallyhim/awesome-claude-code	52787
multica-ai/andrej-karpathy-skills	204913
ljx1230/agent-tutorial	152
labring/FastGPT	29420
chatanywhere/GPT_API_free	40864
yuyuanweb/ai-passage-creator	302
Chanzhaoyu/chatgpt-web	31502
binarywang/WxJava	33037""".strip().split('\n')

repos = []
for line in lines:
    parts = line.rsplit('\t', 1)
    name = parts[0].strip()
    stars = int(parts[1].strip())
    repos.append({'name': name, 'stars': stars})

repos = [r for r in repos if 'yupi' not in r['name'].lower()]

categories = {
    "AI Agent 框架与运行时": {
        "keywords": ["deepseek-harness", "crewai", "autogen", "openclaw", "pi_agent", "atomcode", "opensquilla", "bitfun", "cc-haha", "anything-llm", "mewcode", "crabcode", "codepilot", "nanzi-ai", "yo-agent", "monkeycode", "hermes-agent", "agent-framework", "agent-runtime", "harness", "agentic-os"],
        "icon": "🤖"
    },
    "Coding Agent 指南与最佳实践": {
        "keywords": ["claude-code-guide", "claudecode-guide", "claude-code-ultimate", "codexguide", "harness_engineering_guide", "awesome-harness-engineering", "awesome-claude-code", "claude-code", "agent-skills", "agents.md", "openclaw-security", "oh-my-claudecode", "cezar", "andrej-karpathy", "open-code-review", "ai-todo", "awesome-cursorrules"],
        "icon": "📘"
    },
    "AI Agent 开发教程与学习路径": {
        "keywords": ["agentguide", "ai-agent-interview-guide", "ai-agents-from-zero", "hello-agents", "agent-tutorial", "prompt-engineering-guide", "ai-system-design-guide", "ai-agent-daily-mentor", "learn-codebase", "mcp-chinese", "deepeseek-harness-guide"],
        "icon": "🎓"
    },
    "RAG 与知识管理": {
        "keywords": ["ragflow", "fastgpt", "flagembedding", "semantica", "openviking", "xinggraph", "agriculture_knowledgegraph", "bge-m3", "ragflow-admin", "ragflow-plus"],
        "icon": "🧠"
    },
    "电商与搜索工程": {
        "keywords": ["ecommerce", "mall4j", "spree", "saleor", "storefront-ui", "marqo", "findegil", "jdsearch", "shopee", "tailwind-ecommerce", "shop-mind", "ai-shopmind", "enthusiast", "ecom-details", "kolzsticks", "kernel-memory-ecommerce", "intelligent-product-search", "e-com", "e-commerce", "shop"],
        "icon": "🛒"
    },
    "DSH 插件生态": {
        "keywords": ["dsh-plugin", "dsh-tabbit", "dsh-web-ui", "dsh-tui", "dsh-oil", "dsh-solo", "dsh-balance", "dsh-better-sidebar", "dsh-deep-whale", "dsh-plugins-marketplace", "deepseek-harness-android", "deepseek-harness-desktop", "tianshu-harness", "martty", "deepsee", "anysearch-dsh", "see-skill"],
        "icon": "🔌"
    },
    "内容创作与媒体工具": {
        "keywords": ["douyin", "tiktok", "xiaohongshu", "xhs-downloader", "bilix", "media", "oil-cover", "viral_writer", "wechat_content", "toutiao", "crawler", "mediacrawler", "crawlertutorial", "ai-ttv-workflow", "wecms", "syncmaster", "md-wechat", "madopic", "xiaohongshu-article-cards", "resume-design", "syncmaster", "syncmaster", "inkai", "inakai"],
        "icon": "📱"
    },
    "Java 后端与 Spring 生态": {
        "keywords": ["javaguide", "deepseek4j", "wxjava", "lz-litchi", "ruoyi", "aias", "walioffice", "binarywang", "cs-base", "ai-passage-creator", "spring"],
        "icon": "☕"
    },
    "前端与 UI 组件库": {
        "keywords": ["assistant-ui", "storefront-ui", "minimal-mistakes", "portfolio", "ink", "tui.editor", "awesome-tuis", "lieflat", "hallmark", "diagram-design", "architecture-diagram", "math-curve", "html-light", "html-design", "element-plus", "buidai", "chatgpt-web", "grok-clone", "grok-desktop", "marvis-office", "china-heritage-3d"],
        "icon": "🎨"
    },
    "桌面宠物与 AI 陪伴": {
        "keywords": ["petdex", "remielle", "desktoppet", "doraemon", "pet-mochi", "my-neuro", "open-llm-vtuber", "xiaolu", "desktop-pet", "gqy", "petgenerator"],
        "icon": "🐾"
    },
    "LLM API 网关与代理": {
        "keywords": ["9router", "relayapi", "grok2api", "tabbit2api", "gpt_api_free", "chatanywhere", "langsearch", "grok-cli", "grok2api", "decolua"],
        "icon": "🔁"
    },
    "Agent Skills & 技能合集": {
        "keywords": ["awesome-agent-skills", "awesome-skills", "awesome-clawdbot", "claude-skills", "linkfox-skills", "ecommerce-skills", "agnes-ai", "gpt-image2", "viral_writer", "life-to-comic", "ache", "jezweb", "emilkowalski/skills", "buainoai", "skill"],
        "icon": "⚡"
    },
    "数据科学与 AI 基础设施": {
        "keywords": ["elasticsearch", "qdrant", "unlimited-ocr", "public-apis", "free-for-dev", "free-sms", "roborgen", "hok_env", "elasticsearch-labs", "mcp-server-12306", "mcp-jobs", "docu-crawler", "ossput", "beautify-github-profile", "readme-ai", "paperclip", "macro", "mymagicpower"],
        "icon": "📊"
    },
    "AI 聊天机器人与社交平台": {
        "keywords": ["kirara", "qqbot", "knowe", "grok-desktop", "grok-cli", "grok-clone", "jsoft", "yuque", "chatgpt-web"],
        "icon": "💬"
    }
}

categorized = {}
assigned = set()
for cat_name, cat_info in categories.items():
    categorized[cat_name] = []
    for r in repos:
        if r['name'] in assigned:
            continue
        name_lower = r['name'].lower()
        for kw in cat_info['keywords']:
            if kw.lower() in name_lower:
                categorized[cat_name].append(r)
                assigned.add(r['name'])
                break

unassigned = [r for r in repos if r['name'] not in assigned]

for cat_name, cat_repos in sorted(categorized.items(), key=lambda x: -len(x[1])):
    icon = categories[cat_name]['icon']
    print(f"\n{icon} **{cat_name}** ({len(cat_repos)} repos)")
    for r in sorted(cat_repos, key=lambda x: -x['stars']):
        print(f"   - [{r['name']}](https://github.com/{r['name']}) ⭐{r['stars']}")

print(f"\n\n📌 **未分类** ({len(unassigned)} repos)")
for r in sorted(unassigned, key=lambda x: -x['stars']):
    print(f"   - [{r['name']}](https://github.com/{r['name']}) ⭐{r['stars']}")

print(f"\n\n总计: {len(repos)} repos, 已分类: {len(assigned)}, 未分类: {len(unassigned)}")