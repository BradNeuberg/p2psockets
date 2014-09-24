package org.scache;

/*
 *  Smart Cache, http proxy cache server
 *  Copyright (C) 1998-2003 Radim Kolar
 *
 *    Smart Cache is Open Source Software; you may redistribute it
 *  and/or modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2, or (at your option) any later version.
 *
 *    This program distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *    A copy of the GNU General Public License is available as
 *  /usr/doc/copyright/GPL in the Debian GNU/Linux distribution or on
 *  the World Wide Web at http://www.gnu.org/copyleft/gpl.html. You
 *  can also obtain it by writing to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

import java.io.*;
import java.util.*;
import java.net.*;

public final class scache
{

public static String VERSION;

public final static String CACHENAME="Smart Cache";
public final static String CACHEVER="0.88";
public static String CFGFILE="scache.cnf";
public static String GCFGFILE="gc.cnf";

public static boolean faststart;
public static String cfgdir=".";
public static int listenbacklog;

public static int MAXHTTPCLIENTS=30;

private transient ServerSocket server;

private final static int ACT_RUN=0;
private final static int ACT_GC=1;
private final static int ACT_FAKEGC=2;
private final static int ACT_EXIT=4;
private final static int ACT_KILLUNREF=8;
private final static int ACT_REBALANCE=16;
private final static int ACT_LOCAL=32;
private final static int ACT_REPAIR=64;
private final static int ACT_NOCACHE=128;

/*
 * IF YOU DO NOT LIKE THINGS LIKE THIS, FEEL FREE TO DELETE OR REPLACE
 * THEM BY YOURS FAVOURITE. 
 *
 * QUOTES INCLUDES FULL VERSION OF `SRI ISOPANISAD`
 *    Hare Krishna
 */

public final static String[] quotes=
{
 /* classic http://www.iskcon.org/sastra/ */
"\"I was born in the darkest ignorance, and my spiritual master pierced my eyes\n"+
"with the torch of knowledge. I offer my respectful obeisances unto him.\"\n\t\t\t\t- Sri Guru Pranama",
/* the most known mantra in the world, recommended for present age */
"HARE KRSNA HARE KRSNA\nKRSNA KRSNA HARE HARE\nHARE RAMA HARE RAMA\nRAMA RAMA HARE HARE\n\t\t\t\t- Hare Krsna Maha-mantra",

/* Narasimha */
"\"I offer my obeisances to Lord Narasimha, who gives joy to Prahlada Maharaja and\nwhose nails are like chisels on the stonelike chest of the demon Hiranyakasipu.\n\nLord Nrsimha is here and also there. Wherever I go Lord Nrsimha is there. He is\nin the heart and is outside as well. I surrender to Lord Nrsimha, the origin of\nall things and the supreme refuge.\"\n\t\t\t\t- Sri Nrsimha Pranama",

"\"I offer my respectful obeisances unto all the Vaisnava devotees of the Lord.\nThey are just like desire trees who can fulfill the desires of everyone, and\nthey are full of compassion for the fallen conditioned souls.\"\n\t\t\t\t- Sri Vaisnava Pranama",

"\"O my dear Krsna, ocean of mercy, You are the friend of the distressed and the\nsource of creation. You are the master of the cowherd men and the lover of the\ngopis, especially Radharani. I offer my respectful obeisances unto You.\"\n\t\t\t\t- Sri Krsna Pranama",


"\"I offer my repeated obeisances unto Vrnda, Srimati Tulasi Devi, who is very\ndear to Lord Kesava. O goddess, you bestow devotional service to Lord Krsna\nand possess the highest truth.\"\n\t\t\t\t- Sri Tulasi Pranama",

"\"O Kesava! O Lord of the universe! O Lord Hari, who have assumed the form of\nhalf-man, half-lion! All glories to You! Just as one can easily crush a wasp\nbetween one's fingernails, so in the same way the body of the wasp like demon\nHiranyakasipu has been ripped apart by the wonderful pointed nails on Your\nbeautiful lotus hands.\"\n\t\t\t\t- Prayer to Lord Nrsimha\n\t\t\t\t\t(from Sri Dasavatara-stotra, p. 97)",

/* Sri Isopanisad -> http://www.iskcon.net/isopanisad/ */

"\"The Personality of Godhead is perfect and complete, and because He is\ncompletely perfect, all emanations from Him, such as this phenomenal world, are\nperfectly equipped as complete wholes. Whatever is produced of the Complete\nWhole is also complete in itself. Because He is the Complete Whole, even though\nso many complete units emanate from Him, He remains the complete balance.\"\n\t\t\t- Sri Isopanisad, Invocation",

"\"Everything animate or inanimate that is within the universe is controlled and\nowned by the Lord. One should therefore accept only those things necessary for\nhimself, which are set aside as his quota, and one should not accept other\nthings, knowing well to whom they belong.\"\n\t\t\t\t- Sri Isopanisad, mantra 1",

"\"One may aspire to live for hundreds of years if he continuously goes on working\nin that way, for that sort of work will not bind him to the law of karma.\nThere is no alternative to this way for man.\"\n\t\t\t\t- Sri Isopanisad, mantra 2",

"\"The killer of the soul, whoever he may be, must enter into the planets known as\nthe worlds of the faithless, full of darkness and ignorance.\"\n\t\t\t\t- Sri Isopanisad, mantra 3",

"\"Although fixed in His abode, the Personality of Godhead is swifter than the\nmind and can overcome all others running. The powerful demigods cannot approach\nHim. Although in one place, He controls those who supply the air and rain.\nHe surpasses all in excellence.\"\n\t\t\t- Sri Isopanisad, mantra 4",

"\"The Supreme Lord walks and does not walk. He is far away, but He is very near\nas well. He is within everything, and yet He is outside of everything.\"\n\t\t\t\t- Sri Isopanisad, mantra 5",

"\"He who sees everything in relation to the Supreme Lord, who sees all living\nentities as His parts and parcels, and who sees the Supreme Lord within\neverything never hates anything or any being.\"\n\t\t\t\t- Sri Isopanisad, mantra 6",


"\"One who always sees all living entities as spiritual sparks, in quality one\nwith the Lord, becomes a true knower of things.\nWhat, then, can be illusion or anxiety for him?\"\n\t\t\t\t- Sri Isopanisad, mantra 7",

"\"Such a person must factually know the greatest of all, the Personality of\nGodhead, who is unembodied, omniscient, beyond reproach, without veins, pure\nand uncontaminated, the self-sufficient philosopher who has been fulfilling\neveryone's desire since time immemorial.\"\n\t\t\t\t- Sri Isopanisad, mantra 8",

"\"Those who engage in the culture of nescient activities shall enter into the\ndarkest region of ignorance. Worse still are those engaged in the culture of\nso-called knowledge.\"\n\t\t\t\t- Sri Isopanisad, mantra 9",

"\"The wise have explained that one result is derived from the culture of\nknowledge and that a different result is obtained from the culture of\nnescience.\"\n\t\t\t\t- Sri Isopanisad, mantra 10",


"\"Only one who can learn the process of nescience and that of transcendental\nknowledge side by side can transcend the influence of repeated birth and death\nand enjoy the full blessings of immortality.\"\n\t\t\t\t- Sri Isopanisad, mantra 11",

"\"Those who are engaged in the worship of demigods enter into the darkest region\nof ignorance, and still more so do the worshipers of the impersonal Absolute.\"\n\t\t\t\t- Sri Isopanisad, mantra 12",

"\"It is said that one result is obtained by worshiping the supreme cause of all\ncauses and that another result is obtained by worshiping what is not supreme.\nAll this is heard from the undisturbed authorities, who clearly explained it.\"\n\t\t\t- Sri Isopanisad, mantra 13",

"\"One should know perfectly the Personality of Godhead Sri Krsna and His\ntranscendental name, form, qualities and pastimes, as well as the temporary\nmaterial creation with its temporary demigods, men and animals. When one knows\nthese, he surpasses death and the ephemeral cosmic manifestation with it, and\nin the eternal kingdom of God he enjoys his eternal life of bliss and\nknowledge.\"\n\t\t\t- Sri Isopanisad, mantra 14",

"\"O my Lord, sustainer of all that lives, Your real face is covered by Your\ndazzling effulgence. Kindly remove that covering and exhibit Yourself to Your\npure devotee.\"\n\t\t\t-  Sri Isopanisad, mantra 15",

"\"O my Lord, O primeval philosopher, maintainer of the universe, O regulating\nprinciple, destination of the pure devotees, well-wisher of the progenitors\nof mankind, please remove the effulgence of Your transcendental rays so that I\ncan see Your form of bliss. You are the eternal Supreme Personality of Godhead,\nlike unto the sun, as am I.\"\n\t\t\t- Sri Isopanisad, mantra 16",

"\"Let this temporary body be burnt to ashes, and let the air of life be merged\nwith the totality of air. Now, O my Lord, please remember all my sacrifices,\nand because You are the ultimate beneficiary, please remember all that I\nhave done for You.\"\n\t\t\t\t- Sri Isopanisad, mantra 17",


"\"O my Lord, as powerful as fire, O omnipotent one, now I offer You all\nobeisances, falling on the ground at Your feet. O my Lord, please lead me on\nthe right path to reach You, and since You know all that I have done in the\npast, please free me from the reactions to my past sins so that there will be\nno hindrance to my progress.\"\n\t\t\t\t- Sri Isopanisad, mantra 18",

 /* BHAGAVAD - GITA quotes -> http://www.gitacd.com */
"\"When irreligion is prominent in the family, O Krsna, the women of the family\nbecome polluted, and from the degradation of womanhood, O descendant of Vrsni,\ncomes unwanted progeny.\"\n\t\t\t\t- Bhagavad-gita 1.40",

"\"An increase of unwanted population certainly causes hellish life both for\nthe family and for those who destroy the family tradition. The ancestors of\nsuch corrupt families fall down, because the performances for offering them\nfood and water are entirely stopped.\"\n\t\t\t\t- Bhagavad-gita 1.41",

"\"By the evil deeds of those who destroy the family tradition and thus give rise\nto unwanted children, all kinds of community projects and family welfare activities are devastated.\"\n\t\t\t\t- Bhagavad-gita 1.42",

"\"O Krsna, maintainer of the people, I have heard by disciplic succession that\nthose who destroy family traditions dwell always in hell.\"\n\t\t\t\t- Bhagavad-gita 1.43",
 
"\"Never there was a time when I did not exist, nor you, nor all these kings;\nnor in the future shall any of as cease to be.\"\n\t\t\t\t- Bhagavad-gita 2.12",

"\"As the embodied soul continuously passes, in this body, from boyhood to youth\nto old age, the soul similarly passes into another body at death. A sober person\nis not bewildered by such a change.\"\n\t\t\t\t- Bhagavad-gita 2.13",

"\"Those who are seers of the truth have concluded that of the nonexistent\n[the material body] there is no endurance and of the eternal [the soul]\nthere is no change. This they have concluded by studying the nature of both.\"\n\t\t\t\t- Bhagavad-gita 2.16",

"\"That which pervades the entire body you should know to be indestructible.\nNo one is able to destroy that imperishable soul.\"\n\t\t\t\t- Bhagavad-gita 2.17",

"\"For the soul there is neither birth nor death at any time. He has not come into\nbeing, does not come into being, and will not come into being. He is unborn,\neternal, ever-existing and primeval. He is not slain when the body is slain.\"\n\t\t\t\t- Bhagavad-gita 2.20",

"\"As a person puts on new garments, giving up old ones, the soul similarly\naccepts new material bodies, giving up the old and useless ones.\"\n\t\t\t\t- Bhagavad-gita 2.22",

"\"Some look on the soul as amazing, some describe him as amazing, and some hear\nof him as amazing, while others, even after hearing about him, cannot\nunderstand him at all.\"\n\t\t\t\t- Bhagavad-gita 2.29",    

"\"One who is not connected with the Supreme [in Krsna consciousness] can have\nneither transcendental intelligence nor a steady mind, without which there is\nno possibility of peace. And how can there be any happiness without peace?\"\n\t\t\t\t- Bhagavad-gita 2.66",

"\"Whatever action a great man performs, common men follow. And whatever standards\nhe sets by exemplary acts, all the world pursues.\"\n\t\t\t\t- Bhagavad-gita 3.21",

"\"Just try to learn the truth by approaching a spiritual master. Inquire from\nhim submissively and render service unto him. The self-realized souls can\nimpart knowledge unto you because they have seen the truth.\"\n\t\t\t\t- Bhagavad-gita 4.34",

"\"For him who has conquered the mind, the mind is the best of friends;\nbut for one who has failed to do so, his mind will remain the greatest enemy.\"\n\t\t\t\t- Bhagavad-gita 6.6",

"\"A person is said to be established in self-realization and is called a\nyogi [or mystic] when he is fully satisfied by virtue of acquired knowledge\nand realization. Such a person is situated in transcendence and is\nself-controlled. He sees everything--whether it be pebbles, stones or gold --\n -- as the same.\"\n\t\t\t\t- Bhagavad-gita 6.8",

"\"The Supreme Personality of Godhead said: Son of Prtha, a transcendentalist\nengaged in auspicious activities does not meet with destruction either in this\nworld or in the spiritual world; one who does good, My friend, is never\novercome by evil.\"\n\t\t\t\t- Bhagavad-gita 6.40",

"\"O conqueror of wealth, there is no truth superior to Me. Everything rests\nupon Me, as pearls are strung on a thread.\"\n\t\t\t\t- Bhagavad-gita 7.7",

"\"O son of Kunti, I am the taste of water, the light of the sun and the moon,\nthe syllable om in the Vedic mantras; I am the sound in ether and ability in\nman.\"\n\t\t\t\t- Bhagavad-gita 7.8",

"\"I am the original fragrance of the earth, and I am the heat in fire.\nI am the life of all that lives, and I am the penances of all ascetics.\"\n\t\t\t\t- Bhagavad-gita 7.9",

"\"O son of Prtha, know that I am the original seed of all existences,\nthe intelligence of the intelligent, and the prowess of all powerful men.\"\n\t\t\t\t- Bhagavad-gita 7.10",

"\"I am the strength of the strong, devoid of passion and desire. I am sex life\nwhich is not contrary to religious principles, O lord of the Bharatas [Arjuna].\"\n\t\t\t\t- Bhagavad-gita 7.11",

"\"Know that all states of being--be they of goodness, passion or ignorance--are\nmanifested by My energy. I am, in one sense, everything, but I am independent.\nI am not under the modes of material nature, for they, on the contrary, are\nwithin Me.\"\n\t\t\t\t- Bhagavad-gita 7.12",

"\"Those miscreants who are grossly foolish, who are lowest among mankind, whose\nknowledge is stolen by illusion, and who partake of the atheistic nature of\ndemons do not surrender unto Me.\"\n\t\t\t\t- Bhagavad-gita 7.15",

"\"O best among the Bharatas, four kinds of pious men begin to render devotional\nservice unto Me--the distressed, the desirer of wealth, the inquisitive, and he\nwho is searching for knowledge of the Absolute.\"\n\t\t\t\t- Bhagavad-gita 7.16",

"\"O Arjuna, as the Supreme Personality of Godhead, I know everything that has\nhappened in the past, all that is happening in the present, and all things that\nare yet to come. I also know all living entities; but Me no one knows.\"\n\t\t\t\t- Bhagavad-gita 7.26",

"\"Whatever state of being one remembers when he quits his body, O son of Kunti,\nthat state he will attain without fail.\"\n\t\t\t\t- Bhagavad-gita 8.6",

"\"Those who worship the demigods will take birth among the demigods; those who\nworship the ancestors go to the ancestors; those who worship ghosts and spirits\nwill take birth among such beings; and those who worship Me will live with Me.\"\n\t\t\t\t-Bhagavad-gita 9.25",

"\"If one offers Me with love and devotion a leaf, a flower, fruit or water,\nI will accept it.\"\n\t\t\t\tBhagavad-gita 9.26",

"\"Whatever you do, whatever you eat, whatever you offer or give away,\nand whatever austerities you perform -- do that, O son of Kunti,\nas an offering to Me.\"\n\t\t\t\tBhagavad-gita 9.27",

"\"Engage your mind always in thinking of Me, become My devotee, offer obeisances\nto Me and worship Me. Being completely absorbed in Me, surely you will come\nto Me.\"\n\t\t\t\t- Bhagavad-gita 9.34",

"\"I am the Supersoul, O Arjuna, seated in the hearts of all living entities.\nI am the beginning, the middle and the end of all beings.\"\n\t\t\t\t- Bhagavad-gita 10.20",

"\"Of the Adityas I am Visnu, of lights I am the radiant sun, of the Maruts\nI am Marici, and among the stars I am the moon.\"\n\t\t\t\t- Bhagavad-gita 10.21",

"\"Of the Vedas I am the Sama Veda; of the demigods I am Indra, the king of\nheaven; of the senses I am the mind; and in living beings I am the living\nforce [consciousness].\"\n\t\t\t\t- Bhagavad-gita 10.22",

"\"Of all the Rudras I am Lord Siva, of the Yaksas and Raksasas I am the Lord\nof wealth [Kuvera], of the Vasus I am fire [Agni], and of mountains I am Meru.\"\n\t\t\t\t- Bhagavad-gita 10.23",

"\"Of priests, O Arjuna, know Me to be the chief, Brhaspati. Of generals I am\nKartikeya, and of bodies of water I am the ocean.\"\n\t\t\t\t- Bhagavad-gita 10.24",

"\"Of the great sages I am Bhrgu; of vibrations I am the transcendental om. Of\nsacrifices I am the chanting of the holy names [japa], and of immovable\nthings I am the Himalayas.\"\n\t\t\t\t- Bhagavad-gita 10.25",

"\"Of all trees I am the banyan tree, and of the sages among the demigods I am\nNarada. Of the Gandharvas I am Citraratha, and among perfected beings I am\nthe sage Kapila.\"\n\t\t\t\t- Bhagavad-gita 10.26",

"\"Of horses know Me to be Uccaihsrava, produced during the churning of the\nocean for nectar. Of lordly elephants I am Airavata, and among men\nI am the monarch.\"\t\t\t- Bhagavad-gita 10.27",

"\"Of weapons I am the thunderbolt; among cows I am the surabhi. Of causes for\nprocreation I am Kandarpa, the god of love, and of serpents I am Vasuki.\"\n\t\t\t\t- Bhagavad-gita 10.28",

"\"Of the many-hooded Nagas I am Ananta, and among the aquatics I am the demigod\nVaruna. Of departed ancestors I am Aryama, and among the dispensers of law\nI am Yama, the lord of death.\"\n\t\t\t\t- Bhagavad-gita 10.29",

"\"Among the Daitya demons I am the devoted Prahlada, among subduers I am time,\namong beasts I am the lion, and among birds I am Garuda.\"\n\t\t\t\t- Bhagavad-gita 10.30",

"\"Of purifiers I am the wind, of the wielders of weapons I am Rama, of fishes\nI am the shark, and of flowing rivers I am the Ganges.\"\n\t\t\t\t- Bhagavad-gita 10.31",

"\"Of all creations I am the beginning and the end and also the middle, O Arjuna.\nOf all sciences I am the spiritual science of the self, and among logicians\nI am the conclusive truth.\"\n\t\t\t\t- Bhagavad-gita 10.32",

"\"Of letters I am the letter A, and among compound words I am the dual compound.\nI am also inexhaustible time, and of creators I am Brahma.\"\n\t\t\t\t- Bhagavad-gita 10.33",

"\"I am all-devouring death, and I am the generating principle of all that is\nyet to be. Among women I am fame, fortune, fine speech, memory, intelligence,\nsteadfastness and patience.\"\n\t\t\t\t- Bhagavad-gita 10.34",

"\"Of the hymns in the Sama Veda I am the Brhat-sama, and of poetry I am\nthe Gayatri. Of months I am Margasirsa [November-December], and of seasons\nI am flower-bearing spring.\"\n\t\t\t\t- Bhagavad-gita 10.35",

"\"I am also the gambling of cheats, and of the splendid I am the splendor.\nI am victory, I am adventure, and I am the strength of the strong.\"\n\t\t\t\t- Bhagavad-gita 10.36",

"\"Of the descendants of Vrsni I am Vasudeva, and of the Pandavas I am Arjuna.\nOf the sages I am Vyasa, and among great thinkers I am Usana.\"\n\t\t\t\t- Bhagavad-gita 10.37",

"\"Among all means of suppressing lawlessness I am punishment, and of those\nwho seek victory I am morality. Of secret things I am silence, and of the\nwise I am the wisdom.\"\n\t\t\t\t- Bhagavad-gita 10.38",

"\"Furthermore, O Arjuna, I am the generating seed of all existences.\nThere is no being -- moving or nonmoving -- that can exist without Me.\"\n\t\t\t\t- Bhagavad-gita 10.39",

"\"O mighty conqueror of enemies, there is no end to My divine manifestations.\nWhat I have spoken to you is but a mere indication of My infinite opulences.\"\n\t\t\t\t- Bhagavad-gita 10.40",

"\"Know that all opulent, beautiful and glorious creations spring from but a\nspark of My splendor.\"\n\t\t\t\t- Bhagavad-gita 10.41",

"\"The Supreme Personality of Godhead said: Time I am, the great destroyer of\nthe worlds, and I have come here to destroy all people. With the exception of\nyou [the Pandavas], all the soldiers here on both sides will be slain.\"\n\t\t\t\t- Bhagavad-gita 11.32",

"\"The splendor of the sun, which dissipates the darkness of this whole world,\ncomes from Me. And the splendor of the moon and the splendor of fire are also\nfrom Me.\"\n\t\t\t\t- Bhagavad-gita 15.12",

"\"I enter into each planet, and by My energy they stay in orbit. I become the\nmoon and thereby supply the juice of life to all vegetables.\"\n\t\t\t\t- Bhagavad-gita 15.13",

"\"I am the fire of digestion in the bodies of all living entities, and I join\nwith the air of life, outgoing and incoming, to digest the four kinds of\nfoodstuff.\"\n\t\t\t\t- Bhagavad-gita 15.14",

"\"I am seated in everyone's heart, and from Me come remembrance, knowledge and\nforgetfulness. By all the Vedas, I am to be known. Indeed, I am the compiler\nof Vedanta, and I am the knower of the Vedas.\"\n\t\t\t\t- Bhagavad-gita 15.15",

"\"There are three gates leading to this hell -- lust, anger and greed.\nEvery sane man should give these up, for they lead to the degradation\nof the soul.\"\t\t\t- Bhagavad-gita 16.21",

"\"Always think of Me, become My devotee, worship Me and offer your homage unto\nMe. Thus you will come to Me without fail. I promise you this because you are\nMy very dear friend.\"\n\t\t\t\t- Bhagavad-gita 18.65",

"\"Abandon all varieties of religion and just surrender unto Me. I shall deliver\nyou from all sinful reactions. Do not fear.\"\n\t\t\t\t- Bhagavad-gita 18.66"
};

final public static void main(String argv[])
{
 faststart=false;

 // System.runFinalizersOnExit(true);
 VERSION=CACHENAME+" "+CACHEVER;

 System.err.println(VERSION+" - full featured caching proxy server and web forwarder.\n"+
 "Copyright (c) Radim Kolar 1998-2003. Opensource software; There is NO warranty.\n"+
 "See the GNU General Public License version 2 or later for copying conditions.\n");

 /* print quote to stderr */
 //System.err.println(quotes[(int)(Math.random()/(1f/(float)quotes.length))]);
 //System.err.println("");
 
 /* scan command line for last -cfgdir */
 for(int i=0;i<argv.length;i++)
 {
   if(argv[i].equals("-cfgdir"))
     if(i+1<argv.length)
     {
       cfgdir=argv[i+1];
       if(cfgdir.endsWith(File.separator))
           cfgdir=cfgdir.substring(0,cfgdir.length()-File.separator.length());
       i++;
     }
 }
 
 // read the configuration
 mgr m=new mgr();
 httpreq.mgr=m;
 m.read_config(CFGFILE);

 int ACT=0;
 boolean filesystemchecked=false;
 long gcinterval=0;

 // parse command line options
 for(int i=0;i<argv.length;i++)
 {
    if(argv[i].equals("-cfgdir"))
    {
	i++; // no work required, allready done
    } else
    if(argv[i].equals("-killunref"))  { ACT|=ACT_KILLUNREF|ACT_EXIT;}
     else
    if(argv[i].equals("-ro") || argv[i].equals("-cdrom") ||
       argv[i].equals("-readonly") || argv[i].equals("-read-only"))
          cachedir.readonly=true;
     else
    if(argv[i].equals("-p") ||
       argv[i].equals("-port")
    )
     {
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
      {
        try
	{
          mgr.ourport=Integer.valueOf(argv[i+1]).intValue();
	}
	catch (Exception exx)
	 {
           System.err.println("[ERROR] -p requires a numeric argument");
	 }
	i++;
      } else
           System.err.println("[ERROR] -p <port>");
     } 
       else 
    if(argv[i].equals("-ui_port")
    )
     {
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
      {
              try
	        {
	            ui.uiport=Integer.valueOf(argv[i+1]).intValue();
	        }
              catch (Exception exx)
                {
                    System.err.println("[ERROR] -ui_port requires a numeric argument");
                }
        i++;
      } else
             System.err.println("[ERROR] -ui_port <port>");
     }
      else
    if(argv[i].equals("-import"))
     {
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
      {
        if(filesystemchecked==false) {
	                              m.check_filesystem();
				      filesystemchecked=true;
				     }
        m.dirimport(argv[i+1]);
	i++;
	ACT|=ACT_EXIT;
      } else
         System.out.println("[ERROR] -import <Directory>");
     } else
    if(argv[i].equals("-importcache"))
     {
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
      {
        if(filesystemchecked==false) {
	                              m.check_filesystem();
				      filesystemchecked=true;
				     }
        m.cacheimport(argv[i+1]);
	i++;
	ACT|=ACT_EXIT;
      } else
         System.out.println("[ERROR] -importcache <Directory>");
     }
     else
    if(argv[i].equals("-export")
    || argv[i].equals("-fullexport")
    || argv[i].equals("-lruexport")
    || argv[i].equals("-exportlru")
    || argv[i].equals("-exportfull")
    )
     {
      if(i+2<argv.length && ! argv[i+1].startsWith("-")
                         && ! argv[i+2].startsWith("-") )
      {
        if(filesystemchecked==false) {
	                              m.check_filesystem();
				      filesystemchecked=true;
				     }
        int type;
	type=garbage.EXPORT_FILEDATE;
	if(argv[i].indexOf("full")>0) type=garbage.EXPORT_DATE; else
	if(argv[i].indexOf("lru")>0) type=garbage.EXPORT_LRU;
	
	try
	{
          m.cacheexport(argv[i+1],type,garbage.timestring(argv[i+2]));
	}
	catch (java.lang.NumberFormatException zzz)
	 { m.cacheexport(argv[i+2],type,garbage.timestring(argv[i+1]));
	 }
	i+=2;
	ACT|=ACT_EXIT;
      } else
         System.out.println("[ERROR] -export <Directory> <timediff>");
     }
    else
    if(argv[i].equals("-cachedir") ||
       argv[i].equals("-swapdir") ||
       argv[i].equals("-cache_dir") ||
       argv[i].equals("-cacheroot") ||
       argv[i].equals("-root") ||
       argv[i].equals("-dir")
       )
    {
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
      {
        m.cache_dir=argv[i+1];
	i++;
	filesystemchecked=false;
      } else { System.out.println("[ERROR] -cachedir <Directory> [swap_level1_dirs] [swap_level2_dirs]");
               continue;
	     }
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
      {
       try
       {
         int z=Integer.valueOf(argv[i+1]).intValue();
	 m.swap_level1_dirs=m.swap_level2_dirs=z;
	 i++;
       }
       catch (NumberFormatException nse)
        {continue;}
      }
      if(i+1<argv.length && ! argv[i+1].startsWith("-"))
            {
	           try
		   {
		    int z=Integer.valueOf(argv[i+1]).intValue();
	            m.swap_level2_dirs=z;
	            i++;
	           }
	           catch (NumberFormatException nse)
			           {continue;}
            }
    } else
    if(argv[i].equals("-gc"))  
    { 
	  ACT|=ACT_GC|ACT_EXIT;

	  if(i+1<argv.length && ! argv[i+1].startsWith("-"))
	  {
	   try
	   {
	     gcinterval=garbage.timestring(argv[i+1]);
	     i++;
	   }
           catch (NumberFormatException nse)
           { continue; }
	  }
    }
     else
    if(argv[i].equals("-rebalance")) { ACT|=ACT_REBALANCE|ACT_EXIT;}
     else
    if(argv[i].equals("-fakegc"))  {ACT|=ACT_FAKEGC|ACT_EXIT;}
     else
    if(argv[i].equals("-faststart"))  { faststart=true;}
     else
    if(argv[i].equals("-version"))  { System.out.println(CACHEVER);
                                      System.exit(0);
                                    }
     else
    if(argv[i].equals("-local"))  { ACT|=ACT_LOCAL;
                                  }
     else
    if(argv[i].equals("-nocache"))  { ACT|=ACT_NOCACHE;
                                  }
     else				
    if(argv[i].equals("-direct"))  {
	                     m.http_proxy=null;
	                     m.no_proxy=null;
			     m.https_proxy=null;
	   System.err.println("[INFO] Bypassing parent proxy.");
                                  }
     else
    if(argv[i].equals("-nofail"))  {
	                     m.fail=null;
	                     m.pass=null;
			     m.regex_fail=null;
			     m.fail_filename=null;
			     m.pass_filename=null;
			     m.regex_fail_filename=null;
	   System.err.println("[INFO] URL filter turned off.");
                                  }
      else
    if(argv[i].equals("-cookies"))  {
	                           m.allow_cookies_to=null;
				   m.cookie_filename=null;
	   System.err.println("[INFO] Cookie filter turned off.");
                                  }
     else				
    if(argv[i].equals("-online"))  {
	                     ui.loader_add_missing=ui.OFF;
	                     ui.loader_add_reloads=ui.OFF;
	   System.err.println("[INFO] Offline support turned off.");
                                  }
      else
    if(argv[i].equals("-nolog"))  {
	                     httpreq.logpatterns=null;
	                     httpreq.logfilenames=null;
	   System.err.println("[INFO] Logging turned off.");
                                  }
     else				
    if(argv[i].equals("-notrace"))  {
				 mgr.trace_fail=false;
				 httpreq.trace_url=false;
			         request.trace_request=false;
			         request.trace_reply=false;
			         request.trace_abort=false;
			         request.trace_cookie=false;
                                 cacheobject.trace_refresh=false;
				 ConnectionHandler.trace_keepalive=false;
				 httpreq.trace_inkeepalive=false;
				 mgr.trace_remap=false;
				 mgr.trace_redirect=false;
	   System.err.println("[INFO] Tracing to console turned off.");
                                  }
     else				
    if(argv[i].equals("-fastrefresh"))  {
	                     m.reload_age=0;
			     m.min_age=0;
			     m.max_age=60*60*1000L*24*3;  // max age 3 days
			     m.redir_age=0;
			     m.expire_age=0;
			     m.refresh=null;
			     m.lmfactor=0.2f;
	   System.err.println("[INFO] Refresh engine switched to normal browser behaviour.");
                                  }
     else				
   if(argv[i].equals("-http_proxy")) {
      if(i+2<argv.length && (! argv[i+1].startsWith("-")) && (! argv[i+2].startsWith("-")))
       try
        {
           m.setproxy(argv[i+1],Integer.valueOf(argv[i+2]).intValue(),null,false);
	}
      catch (Exception exx)
        {
	  System.out.println("[ERROR] -http_proxy <Hostname> <Port>");
	}
      finally
       {
        i+=2;
       }
     else
       System.out.println("[ERROR] -http_proxy <Hostname> <Port>");
     } else
   if(argv[i].equals("-ftp_proxy")) {
      if(i+2<argv.length && (! argv[i+1].startsWith("-")) && (! argv[i+2].startsWith("-")))
       try
        {
           m.setproxy(argv[i+1],Integer.valueOf(argv[i+2]).intValue(),null,true);
	}
      catch (Exception exx)
        {
	  System.out.println("[ERROR] -ftp_proxy <Hostname> <Port>");
	}
      finally
       {
        i+=2;
       }
     else
       System.out.println("[ERROR] -ftp_proxy <Hostname> <Port>");
     }

     else
    if(argv[i].equals("-repair"))  { ACT|=ACT_REPAIR|ACT_EXIT;
                                  }
     else
     {
      if(!argv[i].equals("-?") &&
         !argv[i].equals("-h") &&
	 !argv[i].equals("-help"))
               System.out.println("[ERROR] Unknown command-line switch: "+argv[i]);
      System.err.println("usage: scache [ -option ... ]\n\nOptions:"
      +"\n\t-gc [minutes]\tRun garbage collection once or every X minutes"
      +"\n\t-cfgdir <Dir>\tSearch configuration files there"
      +"\n\t-cachedir <Dir> [sw1] [sw2]  Use alternate data directory"
      +"\n\t-rebalance\tRehash directory for new swap levels structure"
      +"\n\t-repair\t\tTry to repair damaged cache structure"
      +"\n\t-p <number>\tRun on cache on alternate port"
      +"\n\t-ui_port <number>\tRun user interface on alternate port"
      +"\n\t-online\t\tTurn off offline hooks"
      +"\n\t-http_proxy <Hostname> <port>  Use alternate parent http_proxy"
      +"\n\t-ftp_proxy <Hostname> <port>  Use alternate proxy for FTP"
      +"\n\t-ro\t\tUse data directory in read-only mode"
      +"\n\t-import <Dir>\tImport data from external source"
      +"\n\t-importcache <Dir>  Import data from another Smart Cache"
      +"\n\t-version\tPrint program version and exit"
      +"\n\t-[full|lru]export <Dir> <TimeDelta>  Export newly modified data to specific directory"
      +"\n\t-local\t\tRun cache in forced local mode - needed for avoiding DNS lookups and connects timeouts on bad TCP configurations"
      +"\n\t-nocache\tDo not save new objects to cache"
      +"\n\t-nofail\t\tTurn off URL filter"
      +"\n\t-nolog\t\tTurn off logging filter"
      +"\n\t-notrace\tTurn off tracing to console"
      +"\n\t-cookies\tTurn off Cookie filter"
      +"\n\t-fastrefresh\tCERN refresh behaviour"
      +"\n\t-direct\t\tDo not use http_proxy"
      +"\n\t-killunref\tRemove bad and inaccessible files from cache. Better is to use -gc or -repair instead."
      +"\n\t-fakegc\t\tSimulate garbage collection, (usefull for gc.cnf debug or benchmark)"
      +"\n\nFor more information consult the Smart Cache Manual."

      );return;
    }
  }
  /* check filesystem sanity */
  if(filesystemchecked==false) m.check_filesystem();
  while(ACT>0)
  {
   if( (ACT & ACT_REBALANCE)>0)
    {
     m.rebalance();
     ACT-=ACT_REBALANCE;
    } else
   if( (ACT & ACT_REPAIR)>0)
    {
     System.out.println(new Date()+" Repairing data directory.");
     repair.repairDir(m.cache_dir,true);
     System.out.println(new Date()+" Done.");
     ACT-=ACT_REPAIR;
    } else
   if( (ACT & ACT_FAKEGC)>0)
    {
     m.fake_garbage_collection();
     ACT-=ACT_FAKEGC;
    } else
   if( (ACT & ACT_GC)>0)
    {
     m.garbage_collection(gcinterval);
     ACT-=ACT_GC;
    } else
   if( (ACT & ACT_KILLUNREF)>0)
    {
     m.kill_unref();
     ACT-=ACT_KILLUNREF;
    } else
   if( (ACT & ACT_NOCACHE)>0)
    {
     m.cacheonly=false;
     m.nocache=mgr.addRegexpToArray("*",null,true);
     ACT-=ACT_NOCACHE;
     System.err.println("[INFO] Turning caching off.");
    } else
   if( (ACT & ACT_LOCAL)>0)
   {
	   m.reload_age=Long.MAX_VALUE;
	   m.min_age=Long.MAX_VALUE;
	   m.max_age=Long.MAX_VALUE;
	   m.redir_age=Long.MAX_VALUE;
	   m.expire_age=Long.MAX_VALUE;
	   m.refresh=null;
	   m.lmfactor=99999;
	   m.no_proxy=null;
	   System.err.println("Smart Cache set to FORCED LOCAL MODE. Will not connect to ANY (localhost included) site.");
	   ACT-=ACT_LOCAL;
  } else
  if( (ACT & ACT_EXIT)>0)
  {
	  ACT-=ACT_EXIT;
	  return;
  }
	
  } /* ACT loop */
  m.go();
}


scache(int port,InetAddress adr)
{
 if(adr!=null)

  try{
  server=new ServerSocket(port,listenbacklog,adr);
  }
  catch(IOException e) {System.err.println("[SMARTCACHE] Fatal error: Cannot bind to my port "+port+"/"+adr.getHostAddress()+"\n[SMARTCACHE]    Reason: "+e);System.exit(3);}
   else
  try{
    server=new ServerSocket(port,listenbacklog);
  }
  catch(IOException e) {System.err.println("[SMARTCACHE] Fatal error: Cannot bind to my port "+port+"/*"+"\n[SMARTCACHE]    Reason: "+e);System.exit(3);}
}

public final void httpdloop()
 {
  ThreadGroup clients;
  httpreq client;
  clients=new ThreadGroup("HTTP-clients");
   while (true ) {
            Socket clientSocket = null;
            client=null;
            try {
                clientSocket = server.accept();
                } catch (IOException e) {
                System.err.println("[SMARTCACHE] Warning: Accept failed: "+e);
                continue;
            }
            if(clients.activeCount()>MAXHTTPCLIENTS)
              try {
              System.err.println(new Date()+" [SMARTCACHE] Warning: Active connection limit ("+MAXHTTPCLIENTS+") reached, request rejected.");
	      clientSocket.setSoLinger(true,0);
              clientSocket.close();}
              catch(IOException e) {}
              finally { continue;}
            client=new httpreq(clientSocket);
            new Thread(clients,client).start();
   }  /* listen */
 }
} /* class scache*/
