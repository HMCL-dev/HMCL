#
# Hello Minecraft! Launcher
# Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

import json
import codecs
import re
import sys
from urllib.parse import urlparse, parse_qs

S = ';'

MOD_SEPARATOR = ','

CURSEFORGE_PATTERN1 = re.compile(
    r'^/minecraft/(mc-mods|modpacks|customization|mc-addons|customization/configuration)/+(?P<modid>[\w-]+)(/(.*?))?$')
CURSEFORGE_PATTERN2 = re.compile(
    r'^/projects/(?P<modid>[\w-]+)(/(.*?))?$')
CURSEFORGE_PATTERN3 = re.compile(
    r'^/mc-mods/minecraft/(?P<modid>[\w-]+)(/(.*?))?$')
CURSEFORGE_PATTERN4 = re.compile(
    r'^/legacy/mc-mods/minecraft/(\d+)-(?P<modid>[\w-]+)'
)


def parseCurseforge(url):
    res = urlparse(url)
    if res.scheme not in ['http', 'https']:
        return ''
    for pattern in [CURSEFORGE_PATTERN1, CURSEFORGE_PATTERN2, CURSEFORGE_PATTERN3, CURSEFORGE_PATTERN4]:
        match = pattern.match(res.path)
        if match:
            return match.group('modid')
    return ''


MCMOD_PATTERN = re.compile(
    r'^http(s)?://www\.mcmod\.cn/(class|modpack)/(?P<modid>\d+)\.html$')


def parseMcmod(url):
    match = MCMOD_PATTERN.match(url)
    if match:
        return match.group('modid')
    return ''


MCBBS_HTML_PATTERN = re.compile(r'/+thread-(?P<modid>\d+)-(\d+)-(\d+).html')
MCBBS_PHP_PATTERN = re.compile(r'')


def parseMcbbs(url):
    res = urlparse(url)
    if res.scheme not in ['http', 'https']:
        return ''
    if res.netloc != 'www.mcbbs.net':
        return ''
    match = MCBBS_HTML_PATTERN.match(res.path)
    if match:
        return match.group('modid')

    query = parse_qs(res.query)

    if res.path == '/forum.php':
        if 'mod' in query and 'tid' in query and query['mod'] == ['viewthread']:
            return query['tid']

    return ''


skip = [
    'Minecraft',
    'The Building Game'
]


if __name__ == '__main__':
    json_name = sys.argv[1] or 'data.json'

    with codecs.open(json_name, mode='r', encoding='utf-8-sig') as jsonfile, codecs.open('data.csv', mode='w', encoding='utf-8') as outfile:
        data = json.load(jsonfile)

        for mod in data:
            chinese_name = mod['name']['main']
            sub_name = mod['name']['sub']
            abbr = mod['name']['abbr']

            if sub_name in skip:
                continue

            if S in chinese_name:
                print('Error! ' + chinese_name)
                exit(1)

            if S in sub_name:
                print('Error! ' + chinese_name)
                exit(1)

            black_lists = [
                'Master Chef'
            ]

            curseforge_id = ''
            mcmod_id = ''
            mcbbs_id = ''
            links = mod['links']['list']
            if 'curseforge' in links and links['curseforge'] and sub_name not in black_lists and chinese_name not in black_lists:
                for link in links['curseforge']:
                    curseforge_id = parseCurseforge(link['url'])
                    if curseforge_id != '':
                        break
                if curseforge_id == '':
                    print('Error curseforge ' + chinese_name)
                    exit(1)
            if 'mcmod' in links and links['mcmod']:
                mcmod_id = parseMcmod(links['mcmod'][0]['url'])
                if mcmod_id == '':
                    print('Error mcmod ' + chinese_name)
                    exit(1)
            if 'mcbbs' in links and links['mcbbs']:
                mcbbs_id = parseMcbbs(links['mcbbs'][0]['url'])
                if mcbbs_id == '':
                    print('Error mcbbs ' + chinese_name)
                    exit(1)

            mod_id = []
            if 'modid' in mod and 'list' in mod['modid']:
                for id in mod['modid']['list']:
                    if MOD_SEPARATOR in id:
                        print('Error mod id!' + id)
                        exit(1)

                    mod_id.append(id)
            mod_ids = MOD_SEPARATOR.join([str(id) for id in mod_id])

            outfile.write(
                f'{curseforge_id}{S}{mcmod_id}{S}{mcbbs_id}{S}{mod_ids}{S}{chinese_name}{S}{sub_name}{S}{abbr}\n')

    print('Success!')
