package app.nutshell

data class CatalogApp(val name: String, val url: String)

data class CatalogCategory(val name: String, val apps: List<CatalogApp>)

/**
 * The ready-made lite apps shown in the gallery: a general directory of popular sites, followed by
 * "FMHY" sections with legitimate sites recommended by the FMHY community directory (fmhy.net).
 * Addresses are plain website links; open any of them and edit it later in Lite App Settings.
 */
object Catalog {
    private val SITES = """
Google|https://www.google.com/
Reddit|https://www.reddit.com/
Amazon|https://www.amazon.com/
Google Maps|https://www.google.com/maps
LinkedIn|https://www.linkedin.com/
Wikipedia|https://www.wikipedia.org/
CNN|https://www.cnn.com/
Axios|https://www.axios.com/
BBC|https://www.bbc.com/news
IMDb|https://www.imdb.com/
Reuters|https://www.reuters.com/
Washington Post|https://www.washingtonpost.com/
BlueSky|https://bsky.app/
Mastodon|https://mastodon.social/
Poki|https://poki.com/
TikTok|https://www.tiktok.com/
Twitch|https://www.twitch.tv/
Voyager for Lemmy|https://vger.app/
Wordle|https://www.nytimes.com/games/wordle/index.html
Ask.fm|https://ask.fm/
Letterboxd|https://letterboxd.com/
Discord|https://discord.com/app
Messages by Google|https://messages.google.com/web
NextDoor|https://nextdoor.com/
OkCupid|https://www.okcupid.com/
Tildes|https://tildes.net/
Tumblr|https://www.tumblr.com/
Weibo|https://weibo.com/
AppScope|https://appscope.app/
Baidu|https://www.baidu.com/
Bing|https://www.bing.com/
DuckDuckGo|https://duckduckgo.com/
JustDial|https://www.justdial.com/
StartPage|https://www.startpage.com/
Yandex|https://yandex.com/
Claude AI|https://claude.ai/
Gemini|https://gemini.google.com/
Character.ai|https://character.ai/
Arena.ai|https://lmarena.ai/
Google AI Studio|https://aistudio.google.com/
Chat GPT|https://chatgpt.com/
You.com|https://you.com/
Hugging Face|https://huggingface.co/
Le Chat|https://chat.mistral.ai/
Perplexity|https://www.perplexity.ai/
New York Times|https://www.nytimes.com/
The Guardian|https://www.theguardian.com/
AccuWeather|https://www.accuweather.com/
Al Jazeera|https://www.aljazeera.com/
AnandTech|https://www.anandtech.com/
Android Central|https://www.androidcentral.com/
Android Police|https://www.androidpolice.com/
Bhaskar|https://www.bhaskar.com/
Bloomberg|https://www.bloomberg.com/
Estadao|https://www.estadao.com.br/
Financial Times|https://www.ft.com/
NOS.nl|https://nos.nl/
Folha de São Paulo|https://www.folha.uol.com.br/
Globo|https://www.globo.com/
Google News|https://news.google.com/
Ha'aretz|https://www.haaretz.com/
Hacker News|https://news.ycombinator.com/
Heise|https://www.heise.de/
Jerusalem Post|https://www.jpost.com/
Medium|https://medium.com/
NBC|https://www.nbcnews.com/
NDTV|https://www.ndtv.com/
NPR|https://www.npr.org/
Slate|https://slate.com/
Spiegel|https://www.spiegel.de/
The Atlantic|https://www.theatlantic.com/
The Hindu|https://www.thehindu.com/
Time|https://time.com/
Veja|https://veja.abril.com.br/
Vibbo|https://www.vibbo.com/
Yahoo! Finance|https://finance.yahoo.com/
AlternativeTo|https://alternativeto.net/
Android Hilfe|https://www.android-hilfe.de/
APK Mirror|https://www.apkmirror.com/
App Sales|https://www.appsales.net/
Caschys Blog|https://stadt-bremerhaven.de/
CNet|https://www.cnet.com/
Droid Life|https://www.droid-life.com/
Engadget|https://www.engadget.com/
Gadget Hacks|https://www.gadgethacks.com/
Gizmodo|https://gizmodo.com/
Jio|https://www.jio.com/
Malware Tips|https://malwaretips.com/
Product Hunt|https://www.producthunt.com/
Slashdot|https://slashdot.org/
Stack Overflow|https://stackoverflow.com/
TechCrunch|https://techcrunch.com/
TechMeme|https://techmeme.com/
The Verge|https://www.theverge.com/
Wired|https://www.wired.com/
XDA Developers|https://www.xda-developers.com/
Addy.io|https://addy.io/
Simple Login|https://simplelogin.io/
FlipKart|https://www.flipkart.com/
AliExpress|https://www.aliexpress.com/
Best Buy|https://www.bestbuy.com/
Costco|https://www.costco.com/
Coupons.com|https://www.coupons.com/
Craigslist|https://www.craigslist.org/
CVS Pharmacy|https://www.cvs.com/
eBay|https://www.ebay.com/
Groupon|https://www.groupon.com/
Hot UK Deals|https://www.hotukdeals.com/
Jabong|https://www.jabong.com/
Myntra|https://www.myntra.com/
PepperFry|https://www.pepperfry.com/
Quikr|https://www.quikr.com/
Retail Me Not|https://www.retailmenot.com/
ShopClues|https://www.shopclues.com/
SlickDeals|https://slickdeals.net/
TaoBao|https://www.taobao.com/
Target|https://www.target.com/
Uber Eats|https://www.ubereats.com/
Walgreens|https://www.walgreens.com/
Woot|https://www.woot.com/
Zepto|https://www.zeptonow.com/
Translate|https://translate.google.com/
TripAdvisor|https://www.tripadvisor.com/
Yelp|https://www.yelp.com/
Zomato|https://www.zomato.com/
Atlas Obscura|https://www.atlasobscura.com/
AirBnB|https://www.airbnb.com/
B&H Photo|https://www.bhphotovideo.com/
Booking.com|https://www.booking.com/
Delta Airlines|https://www.delta.com/
Enterprise|https://www.enterprise.com/
Flight Aware|https://www.flightaware.com/
Fuelly|https://www.fuelly.com/
GasBuddy|https://www.gasbuddy.com/
IRCTC|https://www.irctc.co.in/
Kayak|https://www.kayak.com/
Ola Cabs|https://www.olacabs.com/
Orbitz|https://www.orbitz.com/
Priceline|https://www.priceline.com/
Uber|https://m.uber.com/
United Airlines|https://www.united.com/
Yatra|https://www.yatra.com/
Fandango|https://www.fandango.com/
Rotten Tomatoes|https://www.rottentomatoes.com/
Netflix|https://www.netflix.com/
Vimeo|https://vimeo.com/
AlloCiné|https://www.allocine.fr/
Apple TV|https://tv.apple.com/
E! Online|https://www.eonline.com/
Hotstar|https://www.hotstar.com/
Jio Cinema|https://www.jiocinema.com/
Plex|https://app.plex.tv/
The A.V. Club|https://www.avclub.com/
Trakt.tv|https://trakt.tv/
TubiTV|https://tubitv.com/
TV Guide|https://www.tvguide.com/
Twit.tv|https://twit.tv/
Zee5|https://www.zee5.com/
Excalidraw|https://excalidraw.com/
Figma|https://www.figma.com/
Framer|https://www.framer.com/
Webflow|https://webflow.com/
tldraw|https://www.tldraw.com/
DeepL Translator|https://www.deepl.com/translator
Zoom|https://app.zoom.us/wc
Slack|https://app.slack.com/
TeamViewer Chat|https://www.teamviewer.com/
Telegram|https://web.telegram.org/
Deviant Art|https://www.deviantart.com/
Flickr|https://www.flickr.com/
Giphy|https://giphy.com/
Imgur|https://imgur.com/
Pexels|https://www.pexels.com/
Unsplash|https://unsplash.com/
GitHub|https://github.com/
v0|https://v0.dev/
Replit|https://replit.com/
StackBlitz|https://stackblitz.com/
Volta|https://volta.net/
NOAA Weather|https://www.weather.gov/
Weather Channel|https://weather.com/
Windy|https://www.windy.com/
American Express|https://www.americanexpress.com/
Bank of America|https://www.bankofamerica.com/
Chase|https://www.chase.com/
Citibank|https://www.citi.com/
HDFC Bank|https://www.hdfcbank.com/
ICICI Bank|https://www.icicibank.com/
MoneyControl|https://www.moneycontrol.com/
PayPal|https://www.paypal.com/
PayTM|https://paytm.com/
RBC Canada|https://www.rbcroyalbank.com/
Wells Fargo|https://www.wellsfargo.com/
iCal Studio|https://icalstudio.com/
QRCode Scanner|https://qrcodescan.in/
Airtable|https://airtable.com/
Asana|https://app.asana.com/
Google Apps Script|https://script.google.com/
Clean Up Pictures|https://cleanup.pictures/
Flipboard|https://flipboard.com/
FutureMe|https://www.futureme.org/
I Love PDF|https://www.ilovepdf.com/
Instapaper|https://www.instapaper.com/
Life Hacker|https://lifehacker.com/
Notes|https://keep.google.com/
Sleepy Time|https://sleepyti.me/
Smaller Pictures|https://smaller.pictures/
Todoist|https://todoist.com/app
Toodledo|https://www.toodledo.com/
Wolfram Alpha|https://www.wolframalpha.com/
Zenkit|https://zenkit.com/
Google Calendar|https://calendar.google.com/
Linear|https://linear.app/
Loom|https://www.loom.com/
Miro|https://miro.com/app/
Notion|https://www.notion.so/
PairDrop|https://pairdrop.net/
Strudel|https://strudel.cc/
Trello|https://trello.com/
Whimsical|https://whimsical.com/
Coursera|https://www.coursera.org/
Dictionary|https://www.dictionary.com/
Duolingo|https://www.duolingo.com/
edX|https://www.edx.org/
Instructables|https://www.instructables.com/
Khan Academy|https://www.khanacademy.org/
Libby|https://libbyapp.com/
Open Library|https://openlibrary.org/
Project Gutenberg|https://www.gutenberg.org/
Wikiwand|https://www.wikiwand.com/
Wiktionary|https://www.wiktionary.org/
All Recipes|https://www.allrecipes.com/
OpenTable|https://www.opentable.com/
Starbucks|https://www.starbucks.com/
Swiggy|https://www.swiggy.com/
Guitar Tuner|https://www.guitar-tuner.org/
Bandcamp|https://bandcamp.com/
Last.fm|https://www.last.fm/
Rate Your Music|https://rateyourmusic.com/
Spotify|https://open.spotify.com/
Suno|https://suno.com/
YouTube|https://www.youtube.com/
YouTube Music|https://music.youtube.com/
Apple Music|https://music.apple.com/
MixCloud|https://www.mixcloud.com/
SoundCloud|https://soundcloud.com/
Wavepad|https://www.nch.com.au/wavepad/
GoodReads|https://www.goodreads.com/
Kobo|https://www.kobo.com/
Books A Million|https://www.booksamillion.com/
Internet Archive|https://archive.org/
Go Comics|https://www.gocomics.com/
Google Books|https://books.google.com/
Bleacher Report|https://bleacherreport.com/
CricBuzz|https://www.cricbuzz.com/
CricInfo|https://www.espncricinfo.com/
ESPN|https://www.espn.com/
ESPN Brasil|https://www.espn.com.br/
Globo eSporte|https://ge.globo.com/
NFL|https://www.nfl.com/
NHL|https://www.nhl.com/
Gmail|https://mail.google.com/
FastMail|https://app.fastmail.com/
HEY|https://app.hey.com/
Mail.com|https://www.mail.com/
Proton Mail|https://mail.proton.me/
Outlook|https://outlook.live.com/mail/
Yahoo! Mail|https://mail.yahoo.com/
Dropbox|https://www.dropbox.com/
OneDrive|https://onedrive.live.com/
Google Drive|https://drive.google.com/
Mediafire|https://www.mediafire.com/
Mega|https://mega.nz/
Nest|https://home.nest.com/
Realtor|https://www.realtor.com/
Trulia|https://www.trulia.com/
2048|https://play2048.co/
Inbox Attack|https://inboxattack.com/
Nerdle|https://nerdlegame.com/
Lichess|https://lichess.org/
Quordle|https://www.merriam-webster.com/games/quordle
Minecraft Wiki|https://minecraft.wiki/
Squardle|https://squardle.com/
Air Horner|https://airhorner.com/
SMBC Comics|https://www.smbc-comics.com/
WebToons|https://www.webtoons.com/
XKCD|https://xkcd.com/
Bored Panda|https://www.boredpanda.com/
BuzzFeed|https://www.buzzfeed.com/
PostImages|https://postimages.org/
GlassDoor|https://www.glassdoor.com/
Indeed|https://www.indeed.com/
Stripe Dashboard|https://dashboard.stripe.com/
Training Peaks|https://www.trainingpeaks.com/
AirTel|https://www.airtel.in/
Naukri|https://www.naukri.com/
Outer Space|https://outerspace.com.br/
Tencent QQ|https://www.qq.com/
DQ 地球圖輯隊|https://dq.yam.com/
"""

    private val FMHY_SITES = """
DeepSeek|https://chat.deepseek.com/
Grok|https://grok.com/
Microsoft Copilot|https://copilot.microsoft.com/
Poe|https://poe.com/
Qwen Chat|https://chat.qwen.ai/
OpenRouter|https://openrouter.ai/
NotebookLM|https://notebooklm.google.com/
T3 Chat|https://t3.chat/
Pi|https://pi.ai/
Meta AI|https://www.meta.ai/
Kimi|https://www.kimi.com/
Proton|https://proton.me/
Bitwarden|https://vault.bitwarden.com/
Have I Been Pwned|https://haveibeenpwned.com/
VirusTotal|https://www.virustotal.com/
SearXNG Instances|https://searx.space/
Mullvad|https://mullvad.net/
uBlock Origin|https://ublockorigin.com/
Privacy Guides|https://www.privacyguides.org/
AdGuard|https://adguard.com/
Cover Your Tracks|https://coveryourtracks.eff.org/
Tuta Mail|https://app.tuta.com/
Brave Search|https://search.brave.com/
Ecosia|https://www.ecosia.org/
Qwant|https://www.qwant.com/
Simkl|https://simkl.com/
MyAnimeList|https://myanimelist.net/
AniList|https://anilist.co/
Backloggd|https://backloggd.com/
Glitchwave|https://glitchwave.com/
StoryGraph|https://app.thestorygraph.com/
League of Comic Geeks|https://leagueofcomicgeeks.com/
Squoosh|https://squoosh.app/
Photopea|https://www.photopea.com/
Remove.bg|https://www.remove.bg/
TinyPNG|https://tinypng.com/
Wayback Machine|https://web.archive.org/
CyberChef|https://gchq.github.io/CyberChef/
Speedtest|https://www.speedtest.net/
Fast.com|https://fast.com/
Down For Everyone|https://downforeveryoneorjustme.com/
Pluto TV|https://pluto.tv/
Kanopy|https://www.kanopy.com/
Hoopla|https://www.hoopladigital.com/
PBS|https://www.pbs.org/
Dailymotion|https://www.dailymotion.com/
Odysee|https://odysee.com/
Rumble|https://rumble.com/
Free Music Archive|https://freemusicarchive.org/
Jamendo|https://www.jamendo.com/
Musopen|https://musopen.org/
Itch.io|https://itch.io/
GOG|https://www.gog.com/
Epic Games Store|https://store.epicgames.com/
Steam|https://store.steampowered.com/
Humble Bundle|https://www.humblebundle.com/
IsThereAnyDeal|https://isthereanydeal.com/
SteamDB|https://steamdb.info/
Emulation Wiki|https://emulation.gametechwiki.com/
Standard Ebooks|https://standardebooks.org/
Wikisource|https://wikisource.org/
LibriVox|https://librivox.org/
Open Culture|https://www.openculture.com/
Wikimedia Commons|https://commons.wikimedia.org/
ManyBooks|https://manybooks.net/
HathiTrust|https://www.hathitrust.org/
MIT OpenCourseWare|https://ocw.mit.edu/
freeCodeCamp|https://www.freecodecamp.org/
The Odin Project|https://www.theodinproject.com/
Codecademy|https://www.codecademy.com/
Anki Web|https://ankiweb.net/
Quizlet|https://quizlet.com/
Desmos|https://www.desmos.com/calculator
GeoGebra|https://www.geogebra.org/
LibreTexts|https://libretexts.org/
Roadmap.sh|https://roadmap.sh/
Exercism|https://exercism.org/
Learn X in Y Minutes|https://learnxinyminutes.com/
CodePen|https://codepen.io/
CodeSandbox|https://codesandbox.io/
GitLab|https://gitlab.com/
npm|https://www.npmjs.com/
PyPI|https://pypi.org/
Docker Hub|https://hub.docker.com/
Vercel|https://vercel.com/
Netlify|https://app.netlify.com/
Kaggle|https://www.kaggle.com/
Google Colab|https://colab.research.google.com/
Regex101|https://regex101.com/
Can I Use|https://caniuse.com/
DevDocs|https://devdocs.io/
MDN Web Docs|https://developer.mozilla.org/
ArchWiki|https://wiki.archlinux.org/
DistroWatch|https://distrowatch.com/
F-Droid|https://f-droid.org/
OMG! Ubuntu|https://www.omgubuntu.co.uk/
It's FOSS|https://itsfoss.com/
Phoronix|https://www.phoronix.com/
"""

    private val CATEGORIES = """
Popular: Google, Reddit, Amazon, Google Maps, LinkedIn, Wikipedia, CNN, Axios, BBC, IMDb, Reuters, Washington Post, BlueSky, Mastodon, Poki, TikTok, Twitch, Voyager for Lemmy, Wordle
Social: Reddit, LinkedIn, Ask.fm, BlueSky, Letterboxd, Mastodon, Discord, Messages by Google, NextDoor, OkCupid, TikTok, Tildes, Tumblr, Voyager for Lemmy, Weibo
Search: Google, AppScope, Baidu, Bing, DuckDuckGo, JustDial, StartPage, Yandex
AI - Artificial Intelligence: Claude AI, Gemini, Character.ai, Arena.ai, Google AI Studio, Chat GPT, You.com, Hugging Face, Le Chat, Perplexity
News: CNN, Axios, BBC, New York Times, Reuters, The Guardian, Washington Post, AccuWeather, Al Jazeera, AnandTech, Android Central, Android Police, Bhaskar, Bloomberg, BlueSky, Estadao, Financial Times, NOS.nl, Folha de São Paulo, Globo, Google News, Ha'aretz, Hacker News, Heise, Jerusalem Post, Mastodon, Medium, NBC, NDTV, NPR, Slate, Spiegel, The Atlantic, The Hindu, Tildes, Time, Veja, Vibbo, Yahoo! Finance
Technology: AlternativeTo, AnandTech, Android Central, Android Hilfe, Android Police, APK Mirror, App Sales, BlueSky, Caschys Blog, CNet, Droid Life, Engadget, Gadget Hacks, Gizmodo, Hacker News, Jio, Malware Tips, Product Hunt, Slashdot, Stack Overflow, TechCrunch, TechMeme, The Verge, Wired, XDA Developers
Privacy: Addy.io, Simple Login
Shopping: FlipKart, Amazon, AliExpress, Best Buy, Costco, Coupons.com, Craigslist, CVS Pharmacy, eBay, Groupon, Hot UK Deals, Jabong, JustDial, Myntra, PepperFry, Quikr, Retail Me Not, ShopClues, SlickDeals, TaoBao, Target, Uber Eats, Walgreens, Woot, Zepto
Travel: Translate, TripAdvisor, Yelp, Zomato, Google Maps, Atlas Obscura, AirBnB, B&H Photo, Booking.com, Delta Airlines, Enterprise, Flight Aware, Fuelly, GasBuddy, IRCTC, Kayak, Ola Cabs, Orbitz, Priceline, Uber, United Airlines, Yatra
Movies and TV: Fandango, IMDb, Rotten Tomatoes, Netflix, Vimeo, AlloCiné, Apple TV, E! Online, Hotstar, Jio Cinema, Letterboxd, Plex, The A.V. Club, Trakt.tv, TubiTV, TV Guide, Twit.tv, Zee5
Graphics: Excalidraw, Figma, Framer, Webflow, tldraw
Communication: DeepL Translator, Messages by Google, Zoom, Slack, TeamViewer Chat, Telegram
Art and Photography: B&H Photo, Deviant Art, Flickr, Giphy, Imgur, Pexels, TikTok, Unsplash
Programming: GitHub, v0, Replit, StackBlitz, Volta
Weather: AccuWeather, NOAA Weather, Weather Channel, Windy
Banking & Financial: American Express, Bank of America, Chase, Citibank, HDFC Bank, ICICI Bank, MoneyControl, PayPal, PayTM, RBC Canada, Wells Fargo
Tools & Productivity: iCal Studio, Translate, QRCode Scanner, Excalidraw, Airtable, Asana, Google Apps Script, Clean Up Pictures, DeepL Translator, Flipboard, FutureMe, I Love PDF, Instapaper, Life Hacker, Notes, Sleepy Time, Smaller Pictures, Todoist, Toodledo, Wolfram Alpha, Zenkit, Google Calendar, Linear, Loom, Miro, Notion, PairDrop, Strudel, Trello, Whimsical
Education: Wikipedia, Coursera, DeepL Translator, Dictionary, Duolingo, edX, I Love PDF, Instructables, Khan Academy, Libby, Open Library, Project Gutenberg, Wikiwand, Wiktionary, Wolfram Alpha
Food and Cuisine: Yelp, Zomato, All Recipes, JustDial, OpenTable, Starbucks, Swiggy
Music & Videos: Guitar Tuner, Bandcamp, Last.fm, Libby, Rate Your Music, Spotify, Suno, YouTube, YouTube Music, Apple Music, MixCloud, SoundCloud, Wavepad
Books: Amazon, GoodReads, Kobo, Books A Million, Internet Archive, Go Comics, Google Books, Libby, Open Library
Sports: Bleacher Report, CricBuzz, CricInfo, ESPN, ESPN Brasil, Globo eSporte, NFL, NHL
Email: Gmail, Addy.io, FastMail, HEY, Mail.com, Proton Mail, Outlook, Simple Login, Yahoo! Mail
Storage: Dropbox, OneDrive, Google Drive, Mediafire, Mega
Home and Lifestyle: Nest, Realtor, Trulia, Uber Eats
Games: 2048, Inbox Attack, Nerdle, Lichess, Poki, Quordle, Minecraft Wiki, Squardle, Twitch, Wordle
Fun: Air Horner, Go Comics, Poki, Voyager for Lemmy
Comics: Go Comics, SMBC Comics, WebToons, XKCD
Social Content: Bored Panda, BuzzFeed, Giphy, Imgur, PostImages, Voyager for Lemmy
Business: GlassDoor, I Love PDF, Indeed, Stripe Dashboard
Fitness: Training Peaks
USA: CNN, Fandango, Chase, Citibank, CVS Pharmacy, Libby, NBC, NFL, NHL, NPR, Realtor, Target, TV Guide, Walgreens
India: FlipKart, Zomato, AirTel, Bhaskar, CricBuzz, CricInfo, HDFC Bank, Hotstar, ICICI Bank, IRCTC, Jabong, Jio, JustDial, MoneyControl, Myntra, Naukri, NDTV, PayTM, PepperFry, Quikr, ShopClues, The Hindu, Yatra, Zee5, Zepto
Brasil: ESPN Brasil, Estadao, Folha de São Paulo, Globo, Globo eSporte, Outer Space, Veja
Deutschland: Android Hilfe
France: AlloCiné
China: Baidu, TaoBao, Weibo, Tencent QQ
Israel: Ha'aretz, Jerusalem Post
Canada: RBC Canada
Korea: WebToons
UK: BBC, Hot UK Deals
Russia: Yandex
Taiwan: DQ 地球圖輯隊
FMHY · AI Tools: DeepSeek, Grok, Microsoft Copilot, Poe, Qwen Chat, OpenRouter, NotebookLM, T3 Chat, Pi, Meta AI, Kimi, Perplexity, Hugging Face
FMHY · Privacy & Security: Proton, Proton Mail, Tuta Mail, Bitwarden, Have I Been Pwned, VirusTotal, SearXNG Instances, Brave Search, Ecosia, Qwant, Mullvad, uBlock Origin, AdGuard, Privacy Guides, Cover Your Tracks, Addy.io, Simple Login
FMHY · Tracking & Discovery: Simkl, Trakt.tv, Letterboxd, MyAnimeList, AniList, Backloggd, Glitchwave, Rate Your Music, Last.fm, GoodReads, StoryGraph, League of Comic Geeks
FMHY · Web Tools: Squoosh, Photopea, Remove.bg, TinyPNG, I Love PDF, PairDrop, Wayback Machine, CyberChef, Speedtest, Fast.com, Down For Everyone, Excalidraw
FMHY · Free & Legal Video: Pluto TV, TubiTV, Plex, Kanopy, Hoopla, PBS, Vimeo, Dailymotion, Odysee, Rumble, Internet Archive
FMHY · Music & Games: Bandcamp, Free Music Archive, Jamendo, Musopen, SoundCloud, Itch.io, GOG, Epic Games Store, Steam, Humble Bundle, IsThereAnyDeal, SteamDB, Emulation Wiki, Lichess
FMHY · Reading & Archives: Internet Archive, Project Gutenberg, Standard Ebooks, Open Library, Wikisource, LibriVox, Open Culture, Wikimedia Commons, ManyBooks, HathiTrust, Libby
FMHY · Learning: MIT OpenCourseWare, freeCodeCamp, The Odin Project, Codecademy, Khan Academy, Coursera, edX, Duolingo, Anki Web, Quizlet, Desmos, GeoGebra, LibreTexts, Roadmap.sh, Exercism, Learn X in Y Minutes
FMHY · Developer: GitHub, GitLab, CodePen, CodeSandbox, StackBlitz, Replit, Stack Overflow, Hacker News, npm, PyPI, Docker Hub, Vercel, Netlify, Kaggle, Google Colab, Regex101, Can I Use, DevDocs, MDN Web Docs
FMHY · Linux & Android: ArchWiki, DistroWatch, F-Droid, APK Mirror, OMG! Ubuntu, It's FOSS, Phoronix, XDA Developers, Android Police
"""

    val categories: List<CatalogCategory> by lazy {
        val registry = HashMap<String, CatalogApp>()
        for (block in listOf(SITES, FMHY_SITES)) {
            block.lines().filter { it.contains('|') }.forEach {
                val (name, url) = it.split('|', limit = 2)
                registry[name.trim()] = CatalogApp(name.trim(), url.trim())
            }
        }
        CATEGORIES.lines().filter { it.contains(": ") }.map { line ->
            val title = line.substringBefore(": ").trim()
            CatalogCategory(title, line.substringAfter(": ").split(", ").mapNotNull { registry[it.trim()] })
        }
    }

    val siteCount: Int get() = categories.flatMap { it.apps }.map { it.url }.distinct().size
}
