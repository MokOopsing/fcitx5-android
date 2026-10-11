"""latin.py places its words on the model's scale by every Latin unigram the model has, counts a
word's pages once whatever its spellings there, and credits the shards its counts are of."""
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import latin

# APP and app on the same 20 pages: under MIN_PAGES, though the two lines' pages sum to 40
COUNTS = '# shards: 000_00007 000_00008\nAPP\t600\t20\napp\t400\t20\nUSB\t1000\t40\nQQ\t100\t30\n'
# USB's unigram has no backoff, QQ's fields are separated by spaces
ARPA = '\\data\\\nngram 1=4\n\n\\1-grams:\n-2.0\tUSB\n-3.0 QQ -0.5\n-1.0\t<s>\t-0.3\n-1.5\t你好\n\n\\end\\\n'


class LatinTest(unittest.TestCase):

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)
        (self.dir / 'reject.tsv').write_text('# word\tkind\tdate\tnote\n', encoding='utf-8')

    def make_pack(self, counts, arpa):
        """latin.py's word pack of these counts and this model, with nothing rejected."""
        counts_path, arpa_path, out_path = (self.dir / name for name in ('latin-counts.tsv', 'lm.arpa', 'latin.words'))
        counts_path.write_text(counts, encoding='utf-8')
        arpa_path.write_text(arpa, encoding='utf-8')
        with mock.patch.object(latin, 'LEXICON', self.dir), contextlib.redirect_stderr(io.StringIO()):
            latin.main(str(counts_path), str(arpa_path), str(out_path))
        return out_path.read_text(encoding='utf-8')

    def test_every_latin_unigram_places_the_scores_and_a_page_counts_once(self):
        self.assertEqual('# youmo words 1\n# layer: latin\n'
                         '# The Latin words typed inside Chinese, from FineWeb-2 cmn_Hani (ODC-By 1.0) shards '
                         '000_00007 and 000_00008;\n'
                         '# made by lexicon/tools/latin.py, 2 words, offset -5.00 from 2 words of the model.\n'
                         "QQ\tQ'Q\t-3.0\nUSB\tU'S'B\t-3.0\n", self.make_pack(COUNTS, ARPA))

    def test_a_rejected_word_goes_in_any_spelling_and_a_case_one_alone(self):
        counts = COUNTS + 'Usb\t500\t40\nSBS\t900\t40\nsbs\t300\t40\n'
        with open(self.dir / 'reject.tsv', 'a', encoding='utf-8') as f:
            f.write('sbs\tobscure\t2026-10-11\t\nUsb\tcase\t2026-10-11\t\n')
        words = [line.split('\t')[0] for line in self.make_pack(counts, ARPA).splitlines() if not line.startswith('#')]
        self.assertEqual(['QQ', 'USB'], sorted(words))

    def test_a_model_without_the_counts_latin_words_is_an_error(self):
        with self.assertRaises(SystemExit) as raised:
            self.make_pack(COUNTS, '\\data\\\nngram 1=1\n\n\\1-grams:\n-1.5\t你好\n\n\\end\\\n')
        self.assertIn('no Latin word of the model seen 100 times or more', str(raised.exception))

    def test_counts_that_do_not_name_their_shards_are_refused(self):
        with self.assertRaises(SystemExit) as raised:
            self.make_pack(COUNTS.split('\n', 1)[1], ARPA)
        self.assertIn("no '# shards:' line first", str(raised.exception))
