import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Tweet;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import ec.util.MersenneTwisterFast;

/**
 * Alice bot.
 * 
 * @author yappy
 */
public class Alice {

	private static final String TWEET_FILE_NAME = "list.txt";
	private static final String REPLY_FILE_NAME = "reply.txt";
	private static final int TWEET_MAX = 140;

	private static MersenneTwisterFast mt = new MersenneTwisterFast();
	private static Scanner sin;
	private static PrintWriter logOut;
	private static Twitter twitter;
	private static List<String> tweetList, replyList;
	private static List<Status> myRecents;

	private static void loadList() throws IOException {
		tweetList = new ArrayList<String>();
		replyList = new ArrayList<String>();

		Scanner in = new Scanner(new File(TWEET_FILE_NAME), "UTF-8");
		while (in.hasNextLine()) {
			String line = in.nextLine();
			if (!line.equals("") && !line.startsWith("#")) {
				if (line.length() > TWEET_MAX) {
					logOut.printf("Warning: %d chars over(%s)%n", TWEET_MAX,
							line);
				}
				tweetList.add(line);
			}
		}
		in = new Scanner(new File(REPLY_FILE_NAME), "UTF-8");
		while (in.hasNextLine()) {
			String line = in.nextLine();
			if (!line.equals("") && !line.startsWith("#")) {
				if (line.length() > TWEET_MAX) {
					logOut.printf("Warning: %d chars over(%s)%n", TWEET_MAX,
							line);
				}
				tweetList.add(line);
				replyList.add(line);
			}
		}
		logOut.printf("Tweet list loaded (%d items)%n", tweetList.size());
		logOut.printf("Reply list loaded (%d items)%n", replyList.size());
	}

	private static void randomTweet() {
		try {
			Set<String> recentSet = new HashSet<String>();
			for (Status status : myRecents) {
				recentSet.add(status.getText());
			}
			if (recentSet.size() >= tweetList.size()) {
				throw new IllegalStateException("dataList is too small");
			}
			String msg;
			do {
				int ind = mt.nextInt(tweetList.size());
				msg = tweetList.get(ind);
			} while (recentSet.contains(msg));
			twitter.updateStatus(msg);
			logOut.println("tweet: " + msg);
		} catch (TwitterException e) {
			e.printStackTrace(logOut);
		}
	}

	private static void autoFollow() {
		try {
			// friend: id who I am following
			// follower: id who is following me
			List<Long> friendList = new ArrayList<Long>();
			List<Long> followerList = new ArrayList<Long>();

			for (long cursor = -1; cursor != 0;) {
				IDs ids = twitter.getFriendsIDs(cursor);
				for (long id : ids.getIDs()) {
					friendList.add(id);
				}
				cursor = ids.getNextCursor();
			}
			logOut.printf("Get friends: %d%n", friendList.size());
			for (long cursor = -1; cursor != 0;) {
				IDs ids = twitter.getFollowersIDs(cursor);
				for (long id : ids.getIDs()) {
					followerList.add(id);
				}
				cursor = ids.getNextCursor();
			}
			logOut.printf("Get followers: %d%n", followerList.size());
			Collections.sort(friendList);
			Collections.sort(followerList);

			// follow
			for (long id : followerList) {
				if (Collections.binarySearch(friendList, id) < 0) {
					User user = twitter.createFriendship(id);
					logOut.printf("follow: %s%n", user.getName());
				}
			}
			// unfollow
			for (long id : friendList) {
				if (Collections.binarySearch(followerList, id) < 0) {
					User user = twitter.destroyFriendship(id);
					logOut.printf("unfollow: %s%n", user.getName());
				}
			}
		} catch (TwitterException e) {
			e.printStackTrace(logOut);
		}
	}

	private static void autoReply() {
		try {
			// last tweet id
			long lastId = myRecents.isEmpty() ? 0 : myRecents.get(0).getId();
			// 200 recent mentions since last tweet
			List<Status> mentions;
			mentions = twitter.getMentions(new Paging(1, 200, lastId));
			for (Status ms : mentions) {
				logOut.printf("Find new mention: %s%n", ms.getText());

				int ind = mt.nextInt(replyList.size());
				String msg = "@" + ms.getUser().getScreenName() + " "
						+ replyList.get(ind);
				StatusUpdate update = new StatusUpdate(msg)
						.inReplyToStatusId(ms.getId());
				twitter.updateStatus(update);

				logOut.printf("Reply: %s%n", msg);
			}
		} catch (TwitterException e) {
			e.printStackTrace(logOut);
		}
	}

	private static class Token {
		private static enum Type {
			ID, NUMBER, STRING, EOF
		}

		public Type type;
		public Object data;

		public Token(Type type, Object data) {
			this.type = type;
			this.data = data;
		}

		@Override
		public String toString() {
			return "[type=" + type + ", data=" + data + "]";
		}
	}

	private static List<Token> lexicalAnalysis(String src) {
		List<Token> result = new ArrayList<Token>();
		StringReader in = new StringReader(src);
		while (true) {
			try {
				// <SKIP> = (space)*
				do {
					in.mark(1);
				} while (Character.isWhitespace((char) in.read()));
				in.reset();
				int first = in.read();
				if (first == -1) {
					result.add(new Token(Token.Type.EOF, null));
					break;
				} else if ((first >= 'a' && first <= 'z')
						|| (first >= 'A' && first <= 'Z')) {
					StringBuilder buf = new StringBuilder();
					buf.append((char) first);
					while (true) {
						in.mark(1);
						int c = in.read();
						if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
								|| (c >= '0' && c <= '9')) {
							buf.append((char) c);
						} else {
							in.reset();
							break;
						}
					}
					result.add(new Token(Token.Type.ID, buf.toString()));
				} else if (first == '"') {

				}
			} catch (IOException e) {
			}
		}
		return result;
	}

	private static void search() {
		try {
			Query query = new Query("北斗");
			QueryResult result = twitter.search(query);
			List<Tweet> ts = result.getTweets();
			System.out.println(ts.size());
			if (!ts.isEmpty()) {
				Tweet t = ts.get(0);

				logOut.printf("Search and decided: %s%n", t.getText());

				int ind = mt.nextInt(tweetList.size());
				String msg = "@" + t.getFromUser() + " " + tweetList.get(ind);
				StatusUpdate update = new StatusUpdate(msg).inReplyToStatusId(t
						.getFromUserId());
				// Caution!
				// twitter.updateStatus(update);

				logOut.printf("Reply: %s%n", msg);
			}
		} catch (TwitterException e) {
			e.printStackTrace(logOut);
		}
	}

	private static void itweet() {
		for (int i = 0; i < tweetList.size(); i++) {
			System.out.printf("%d: %s%n", i, tweetList.get(i));
		}
		System.out.println();
		System.out.printf("Input message No(0..%d)%n", tweetList.size() - 1);
		System.out.println("(Quit to -1)");
		int no = -1;
		if (sin.hasNextInt()) {
			no = sin.nextInt();
		}
		if (no < 0 || no >= tweetList.size()) {
			System.out.println("Quit.");
			return;
		}
		String msg = tweetList.get(no);
		System.out.printf("%d: %s%n", no, msg);
		if (msg.length() > TWEET_MAX) {
			logOut.printf("Warning: %d chars over(%s)%n", TWEET_MAX, msg);
		}
		System.out.println("OK? (y/n)");
		if (!sin.next().startsWith("y")) {
			System.out.println("Quit.");
			return;
		}
		try {
			twitter.updateStatus(msg);
			printlnBoth("tweet: " + msg);
		} catch (TwitterException e) {
			e.printStackTrace(System.out);
			e.printStackTrace(logOut);
		}
	}

	private static void mtweet() {
		System.out.println("Manual tweet mode OK? (y/n)");
		if (!sin.next().startsWith("y")) {
			System.out.println("Quit.");
			return;
		}
		System.out.println("Input message:");
		sin.nextLine();
		String msg = sin.nextLine();
		System.out.printf("Msg: %s%n", msg);
		System.out.println("OK? (y/n)");
		if (!sin.next().startsWith("y")) {
			System.out.println("Quit.");
			return;
		}
		try {
			twitter.updateStatus(msg);
			printlnBoth("tweet: " + msg);
		} catch (TwitterException e) {
			e.printStackTrace(System.out);
			e.printStackTrace(logOut);
		}
	}

	private static void help() {
		System.out.println("--help");
		System.out.println("\tPrint this help.");

		System.out.println("--auto-reply");
		System.out.println("\tFind new @me and reply to it.");
		System.out.println("--random-tweet");
		System.out.println("\tTweet from list.txt at random.");
		System.out.println("--auto-follow");
		System.out.println("\tCheck follow status and auto follow/unfollow.");

		System.out.println("--itweet");
		System.out.println("\tInteractive tweet mode.");
		System.out.println("--mtweet");
		System.out.println("\tManual tweet mode.");
	}

	// TODO
	public static void main(String[] args) {
		System.out.println(lexicalAnalysis("rp abc"));
	}

	/*
	 * public static void main(String[] args) { Set<String> argSet = new
	 * HashSet<String>(Arrays.asList(args));
	 * 
	 * sin = new Scanner(System.in);
	 * 
	 * Date nowDate = new Date(); String nowStr =
	 * String.format("%1$tY%1$tm%1$td", nowDate); File logDir = new File("log");
	 * logDir.mkdir(); String logFileName = nowStr + ".log"; try { Writer w =
	 * new OutputStreamWriter(new FileOutputStream(new File( logDir,
	 * logFileName), true), "UTF-8"); logOut = new PrintWriter(w); } catch
	 * (IOException e) { e.printStackTrace(); System.exit(1); }
	 * logOut.printf("Start (%1$tF %1$tT)%n", nowDate);
	 * 
	 * try { if (argSet.isEmpty()) { printlnBoth("No options. Use --help."); }
	 * if (argSet.remove("--help")) { logOut.println("Help"); help(); }
	 * 
	 * twitter = new TwitterFactory().getInstance(); loadList(); myRecents =
	 * twitter.getUserTimeline(new Paging(1, 10));
	 * logOut.printf("Get user timeline (%d)%n", myRecents.size());
	 * 
	 * if (argSet.remove("--itweet")) {
	 * logOut.println("Interactive tweet mode."); itweet(); } if
	 * (argSet.remove("--mtweet")) { logOut.println("Manual tweet mode.");
	 * mtweet(); } if (argSet.remove("--auto-reply")) {
	 * logOut.println("Auto reply"); autoReply(); } if
	 * (argSet.remove("--random-tweet")) { logOut.println("Random tweet");
	 * randomTweet(); } if (argSet.remove("--auto-follow")) {
	 * logOut.println("Auto follow"); autoFollow(); } if
	 * (argSet.remove("--search")) { logOut.println("Search"); search(); } for
	 * (String arg : argSet) { printlnBoth("Warning: unknown argument " + arg);
	 * } } catch (Exception e) { e.printStackTrace(logOut); }
	 * 
	 * logOut.printf("End (%1$tF %1$tT)%n", System.currentTimeMillis());
	 * logOut.println(); logOut.close(); }
	 */

	/*
	 * println to System.out and logOut
	 */
	private static void printlnBoth(String msg) {
		System.out.println(msg);
		logOut.println(msg);
	}

}
