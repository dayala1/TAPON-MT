package utils;

public class StringUtils {
	public static String removeAccents(String string){
		assert string != null;
		
		String result;
		
		result = new String(string);
		
		result = result.replaceAll("[����]","e");
	    result = result.replaceAll("[��]","u");
	    result = result.replaceAll("[��]","i");
	    result = result.replaceAll("[��]","a");
	    result = result.replaceAll("�","o");

	    result = result.replaceAll("[����]","E");
	    result = result.replaceAll("[��]","U");
	    result = result.replaceAll("[��]","I");
	    result = result.replaceAll("[��]","A");
	    result = result.replaceAll("�","O");
	    
	    return result;
		
	}
}
