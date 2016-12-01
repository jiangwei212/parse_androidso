package com.demo.parseso;

import com.demo.parseso.ElfType32.Elf32_Sym;
import com.demo.parseso.ElfType32.elf32_phdr;
import com.demo.parseso.ElfType32.elf32_shdr;

public class ParseSo {
	
	public static ElfType32 type_32 = new ElfType32();
	
	public static void main(String[] args){
		
		byte[] fileByteArys = Utils.readFile("so/libhello-jni.so");
		if(fileByteArys == null){
			System.out.println("read file byte failed...");
			return;
		}
		
		/**
		 * �Ƚ���so�ļ�
		 * Ȼ���ʼ��AddSection�е�һЩ��Ϣ
		 * �����AddSection
		 */
		parseSo(fileByteArys);
		
		//��ʼ��AddSection�еı���
		AddSection.sectionHeaderOffset = Utils.byte2Int(type_32.hdr.e_shoff);
		AddSection.stringSectionInSectionTableIndex = Utils.byte2Short(type_32.hdr.e_shstrndx);
		AddSection.stringSectionOffset = Utils.byte2Int(type_32.shdrList.get(AddSection.stringSectionInSectionTableIndex).sh_offset);
		//�ҵ���һ�������һ������ΪLoad��Program header��index
		boolean flag = true;
		for(int i=0;i<type_32.phdrList.size();i++){
			if(Utils.byte2Int(type_32.phdrList.get(i).p_type) == 1){//LOAD��type==1,����elf��ʽ�ĵ����ҵ�
				if(flag){
					AddSection.firstLoadInPHIndex = i;
					flag = false;
				}else{
					AddSection.lastLoadInPHIndex = i;
				}
			}
		}
		int lastLoadVaddr = Utils.byte2Int(type_32.phdrList.get(AddSection.lastLoadInPHIndex).p_vaddr);
		int lastLoadMem = Utils.byte2Int(type_32.phdrList.get(AddSection.lastLoadInPHIndex).p_memsz);
		int lastLoadAlign = Utils.byte2Int(type_32.phdrList.get(AddSection.lastLoadInPHIndex).p_align);
		AddSection.addSectionStartAddr = Utils.align(lastLoadVaddr + lastLoadMem, lastLoadAlign);
		System.out.println("start addr hex:"+Utils.bytes2HexString(Utils.int2Byte(AddSection.addSectionStartAddr)));
		
		/**
		 * ���һ��Section
		 * ���µ�˳�򲻿��ң���Ȼ������
		 * 1�����һ��Section Header
		 * 2��ֱ�����ļ���ĩβ׷��һ��section
		 * 3���޸�String�εĳ���
		 * 4���޸�Elf Header�е�section count
		 */
		fileByteArys = AddSection.addSectionHeader(fileByteArys);
		fileByteArys = AddSection.addNewSectionForFileEnd(fileByteArys);
		fileByteArys = AddSection.changeStrtabLen(fileByteArys);
		fileByteArys = AddSection.changeElfHeaderSectionCount(fileByteArys);
		fileByteArys = AddSection.changeProgramHeaderLoadInfo(fileByteArys);
		
		Utils.saveFile("so/libhello-jnis.so", fileByteArys);
		
	}
	
	private static void parseSo(byte[] fileByteArys){
		//��ȡͷ������
		System.out.println("+++++++++++++++++++Elf Header+++++++++++++++++");
		parseHeader(fileByteArys, 0);
		System.out.println("header:\n"+type_32.hdr);

		//��ȡ����ͷ��Ϣ
		System.out.println();
		System.out.println("+++++++++++++++++++Program Header+++++++++++++++++");
		int p_header_offset = Utils.byte2Int(type_32.hdr.e_phoff);
		parseProgramHeaderList(fileByteArys, p_header_offset);
		type_32.printPhdrList();

		//��ȡ��ͷ��Ϣ
		System.out.println();
		System.out.println("+++++++++++++++++++Section Header++++++++++++++++++");
		int s_header_offset = Utils.byte2Int(type_32.hdr.e_shoff);
		System.out.println("offset:"+s_header_offset);
		parseSectionHeaderList(fileByteArys, s_header_offset);
		type_32.printShdrList();

		/*//��ȡ���ű���Ϣ(Symbol Table)
		System.out.println();
		System.out.println("+++++++++++++++++++Symbol Table++++++++++++++++++");
		//������Ҫע����ǣ���Elf����û���ҵ�SymbolTable����Ŀ������������ϸ�۲�Section�е�Type=DYNSYM�ε���Ϣ���Եõ�������εĴ�С��ƫ�Ƶ�ַ����SymbolTable�Ľṹ��С�ǹ̶���16���ֽ�
		//��ô�������Ŀ=��С/�ṹ��С
		//������SectionHeader�в��ҵ�dynsym�ε���Ϣ
		int offset_sym = 0;
		int total_sym = 0;
		for(elf32_shdr shdr : type_32.shdrList){
			if(Utils.byte2Int(shdr.sh_type) == ElfType32.SHT_DYNSYM){
				total_sym = Utils.byte2Int(shdr.sh_size);
				offset_sym = Utils.byte2Int(shdr.sh_offset);
				break;
			}
		}
		int num_sym = total_sym / 16;
		System.out.println("sym num="+num_sym);
		parseSymbolTableList(fileByteArys, num_sym, offset_sym);
		type_32.printSymList();

		//��ȡ�ַ�������Ϣ(String Table)
		System.out.println();
		System.out.println("+++++++++++++++++++Symbol Table++++++++++++++++++");
		//������Ҫע����ǣ���Elf����û���ҵ�StringTable����Ŀ������������ϸ�۲�Section�е�Type=STRTAB�ε���Ϣ�����Եõ�������εĴ�С��ƫ�Ƶ�ַ������������ʱ�����ǲ�֪���ַ����Ĵ�С�����Ծͻ�ȡ������Ŀ��
		//�������ǿ��Բ鿴Section�ṹ�е�name�ֶΣ���ʾƫ��ֵ����ô���ǿ���ͨ�����ֵ����ȡ�ַ����Ĵ�С
		//������ô��⣺��ǰ�ε�nameֵ ��ȥ ��һ�ε�name��ֵ = (��һ�ε�name�ַ����ĳ���)
		//���Ȼ�ȡÿ���ε�name���ַ�����С
		int prename_len = 0;
		int[] lens = new int[type_32.shdrList.size()];
		int total = 0;
		for(int i=0;i<type_32.shdrList.size();i++){
			if(Utils.byte2Int(type_32.shdrList.get(i).sh_type) == ElfType32.SHT_STRTAB){
				int curname_offset = Utils.byte2Int(type_32.shdrList.get(i).sh_name);
				lens[i] = curname_offset - prename_len - 1;
				if(lens[i] < 0){
					lens[i] = 0;
				}
				total += lens[i];
				System.out.println("total:"+total);
				prename_len = curname_offset;
				//������Ҫע����ǣ����һ���ַ����ĳ��ȣ���Ҫ���ܳ��ȼ�ȥǰ��ĳ����ܺ�����ȡ��
				if(i == (lens.length - 1)){
					System.out.println("size:"+Utils.byte2Int(type_32.shdrList.get(i).sh_size));
					lens[i] = Utils.byte2Int(type_32.shdrList.get(i).sh_size) - total - 1;
				}
			}
		}
		for(int i=0;i<lens.length;i++){
			System.out.println("len:"+lens[i]);
		}
		//������Ǹ��������ã����Ƿ���StringTable�е�ÿ���ַ�������������һ��00(��˵�е��ַ���������)����ô����ֻҪ֪��StringTable�Ŀ�ʼλ�ã�Ȼ��Ϳ��Զ�ȡ��ÿ���ַ�����ֵ��
       */
	}
	
	/**
	 * ����Elf��ͷ����Ϣ
	 * @param header
	 */
	private static void  parseHeader(byte[] header, int offset){
		if(header == null){
			System.out.println("header is null");
			return;
		}
		/**
		 *  public byte[] e_ident = new byte[16];
			public short e_type;
			public short e_machine;
			public int e_version;
			public int e_entry;
			public int e_phoff;
			public int e_shoff;
			public int e_flags;
			public short e_ehsize;
			public short e_phentsize;
			public short e_phnum;
			public short e_shentsize;
			public short e_shnum;
			public short e_shstrndx;
		 */
		type_32.hdr.e_ident = Utils.copyBytes(header, 0, 16);//ħ��
		type_32.hdr.e_type = Utils.copyBytes(header, 16, 2);
		type_32.hdr.e_machine = Utils.copyBytes(header, 18, 2);
		type_32.hdr.e_version = Utils.copyBytes(header, 20, 4);
		type_32.hdr.e_entry = Utils.copyBytes(header, 24, 4);
		type_32.hdr.e_phoff = Utils.copyBytes(header, 28, 4);
		type_32.hdr.e_shoff = Utils.copyBytes(header, 32, 4);
		type_32.hdr.e_flags = Utils.copyBytes(header, 36, 4);
		type_32.hdr.e_ehsize = Utils.copyBytes(header, 40, 2);
		type_32.hdr.e_phentsize = Utils.copyBytes(header, 42, 2);
		type_32.hdr.e_phnum = Utils.copyBytes(header, 44,2);
		type_32.hdr.e_shentsize = Utils.copyBytes(header, 46,2);
		type_32.hdr.e_shnum = Utils.copyBytes(header, 48, 2);
		type_32.hdr.e_shstrndx = Utils.copyBytes(header, 50, 2);
	}
	
	/**
	 * ��������ͷ��Ϣ
	 * @param header
	 */
	public static void parseProgramHeaderList(byte[] header, int offset){
		int header_size = 32;//32���ֽ�
		int header_count = Utils.byte2Short(type_32.hdr.e_phnum);//ͷ���ĸ���
		byte[] des = new byte[header_size];
		for(int i=0;i<header_count;i++){
			System.arraycopy(header, i*header_size + offset, des, 0, header_size);
			type_32.phdrList.add(parseProgramHeader(des));
		}
	}
	
	private static elf32_phdr parseProgramHeader(byte[] header){
		/**
		 *  public int p_type;
			public int p_offset;
			public int p_vaddr;
			public int p_paddr;
			public int p_filesz;
			public int p_memsz;
			public int p_flags;
			public int p_align;
		 */
		ElfType32.elf32_phdr phdr = new ElfType32.elf32_phdr();
		phdr.p_type = Utils.copyBytes(header, 0, 4);
		phdr.p_offset = Utils.copyBytes(header, 4, 4);
		phdr.p_vaddr = Utils.copyBytes(header, 8, 4);
		phdr.p_paddr = Utils.copyBytes(header, 12, 4);
		phdr.p_filesz = Utils.copyBytes(header, 16, 4);
		phdr.p_memsz = Utils.copyBytes(header, 20, 4);
		phdr.p_flags = Utils.copyBytes(header, 24, 4);
		phdr.p_align = Utils.copyBytes(header, 28, 4);
		return phdr;
		
	}
	
	/**
	 * ������ͷ��Ϣ����
	 */
	public static void parseSectionHeaderList(byte[] header, int offset){
		int header_size = 40;//40���ֽ�
		int header_count = Utils.byte2Short(type_32.hdr.e_shnum);//ͷ���ĸ���
		byte[] des = new byte[header_size];
		for(int i=0;i<header_count;i++){
			System.arraycopy(header, i*header_size + offset, des, 0, header_size);
			type_32.shdrList.add(parseSectionHeader(des));
		}
	}
	
	private static elf32_shdr parseSectionHeader(byte[] header){
		ElfType32.elf32_shdr shdr = new ElfType32.elf32_shdr();
		/**
		 *  public byte[] sh_name = new byte[4];
			public byte[] sh_type = new byte[4];
			public byte[] sh_flags = new byte[4];
			public byte[] sh_addr = new byte[4];
			public byte[] sh_offset = new byte[4];
			public byte[] sh_size = new byte[4];
			public byte[] sh_link = new byte[4];
			public byte[] sh_info = new byte[4];
			public byte[] sh_addralign = new byte[4];
			public byte[] sh_entsize = new byte[4];
		 */
		shdr.sh_name = Utils.copyBytes(header, 0, 4);
		shdr.sh_type = Utils.copyBytes(header, 4, 4);
		shdr.sh_flags = Utils.copyBytes(header, 8, 4);
		shdr.sh_addr = Utils.copyBytes(header, 12, 4);
		shdr.sh_offset = Utils.copyBytes(header, 16, 4);
		shdr.sh_size = Utils.copyBytes(header, 20, 4);
		shdr.sh_link = Utils.copyBytes(header, 24, 4);
		shdr.sh_info = Utils.copyBytes(header, 28, 4);
		shdr.sh_addralign = Utils.copyBytes(header, 32, 4);
		shdr.sh_entsize = Utils.copyBytes(header, 36, 4);
		return shdr;
	}
	
	/**
	 * ����Symbol Table���� 
	 */
	public static void parseSymbolTableList(byte[] header, int header_count, int offset){
		int header_size = 16;//16���ֽ�
		byte[] des = new byte[header_size];
		for(int i=0;i<header_count;i++){
			System.arraycopy(header, i*header_size + offset, des, 0, header_size);
			type_32.symList.add(parseSymbolTable(des));
		}
	}
	
	private static ElfType32.Elf32_Sym parseSymbolTable(byte[] header){
		/**
		 *  public byte[] st_name = new byte[4];
			public byte[] st_value = new byte[4];
			public byte[] st_size = new byte[4];
			public byte st_info;
			public byte st_other;
			public byte[] st_shndx = new byte[2];
		 */
		Elf32_Sym sym = new Elf32_Sym();
		sym.st_name = Utils.copyBytes(header, 0, 4);
		sym.st_value = Utils.copyBytes(header, 4, 4);
		sym.st_size = Utils.copyBytes(header, 8, 4);
		sym.st_info = header[12];
		//FIXME ������һ�����⣬��������ֶζ�������ֵʼ����0
		sym.st_other = header[13];
		sym.st_shndx = Utils.copyBytes(header, 14, 2);
		return sym;
	}
	

}
