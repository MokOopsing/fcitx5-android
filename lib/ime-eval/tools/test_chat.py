"""chat.py types a run as a chat line means it where a word has two readings: 了 and 着 after a verb
and 不 say it cannot be done, 系 before 鞋带 is ji, and a run with 还给, either reading as likely,
is left out."""
import tempfile
import unittest
from pathlib import Path

import chat

# a word a line, as libime's dictionaries have them: 了, 着, 系, 还 and 得 of two readings each
DICTIONARY = ''.join(line + '\n' for line in (
    '没 mei', '钱 qian', '去 qu', '不 bu', '了 le', '了 liao', '你 ni', '都 dou', '摸 mo', '着 zhe', '着 zhao',
    '我 wo', '系 xi', '系 ji', "鞋带 xie'dai", '呢 ne', '还 hai', '还 huan', '给 gei', '也 ye', '得 de', '得 dei',
))
# every word the unknown word's score: the run split into the fewest words
ARPA = '\\data\\\nngram 1=1\n\n\\1-grams:\n-7.0\t<unk>\n\n\\end\\\n'


class ChatTest(unittest.TestCase):

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        dictionary, arpa = Path(tmp.name) / 'dict.txt', Path(tmp.name) / 'lm.arpa'
        dictionary.write_text(DICTIONARY, encoding='utf-8')
        arpa.write_text(ARPA, encoding='utf-8')
        self.words, self.read = chat.loaders(str(dictionary), str(arpa))

    def test_le_and_zhe_after_a_verb_and_bu_say_it_cannot_be_done(self):
        self.assertEqual('meiqianqubuliao', self.read('没钱去不了'))
        self.assertEqual('nidoumobuzhao', self.read('你都摸不着'))
        # the verb in what came before the run, as make-mixed-set.py passes it
        self.assertEqual('liao', self.read('了', before='去不'))

    def test_bu_le_starting_a_run_is_no_thanks(self):
        self.assertEqual('bule', self.read('不了'))

    def test_xi_before_a_shoelace_is_ji(self):
        self.assertEqual(['我', '系', '鞋带', '呢'], self.words('我系鞋带呢'))
        self.assertEqual('wojixiedaine', self.read('我系鞋带呢'))
        self.assertEqual('woxi', self.read('我系'))

    def test_a_run_with_huan_gei_is_left_out(self):
        self.assertIsNone(self.read('我还给你钱'))
        self.assertEqual('wohaiqu', self.read('我还去'))

    def test_de_after_what_says_it_has_to_be_is_dei(self):
        self.assertEqual('woyedeiqu', self.read('我也得去'))
